package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.discord.AttachmentRejectedException;
import dev.mindforge.discord.AttachmentRejectedException.Reason;
import dev.mindforge.discord.DiscordAttachments;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.NamedAttachmentProxy;

class DiscordAttachmentsTest {

    private static final long MAX_BYTES = 16;
    private static final String CDN_URL = "https://cdn.discordapp.com/attachments/1/2/notes.md?ex=abc&hm=signature";

    private final DiscordAttachments attachments = new DiscordAttachments();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger attachmentsLog = (Logger) LoggerFactory.getLogger(DiscordAttachments.class);

    {
        logs.start();
        attachmentsLog.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        attachmentsLog.detachAppender(logs);
    }

    // AC3

    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.example.org/attachments/notes.md?hm=signature",
        "http://cdn.discordapp.com/attachments/notes.md?hm=signature",
        "https://user:pass@cdn.discordapp.com/attachments/notes.md?hm=signature",
        "https://cdn.discordapp.com:8443/attachments/notes.md?hm=signature",
        "not a url"})
    void aUrlOffTheCdnRule_isRefusedWithoutDownloading(String url) {
        Fixture fixture = makeAttachment(url, CDN_URL, 4, new byte[4]);

        assertThatThrownBy(() -> attachments.read(fixture.attachment(), MAX_BYTES))
            .isInstanceOf(AttachmentRejectedException.class)
            .extracting(e -> ((AttachmentRejectedException) e).reason()).isEqualTo(Reason.NOT_FROM_DISCORD);
        verify(fixture.proxy(), never()).download();
    }

    @Test
    void aProxyUrlOffTheCdn_isRefusedWithoutDownloading() {
        Fixture fixture = makeAttachment(CDN_URL, "https://evil.example.org/x.png", 4, new byte[4]);

        assertThatThrownBy(() -> attachments.read(fixture.attachment(), MAX_BYTES))
            .isInstanceOf(AttachmentRejectedException.class);
        verify(fixture.proxy(), never()).download();
    }

    @Test
    void theMediaProxyHostOnPort443_isAccepted() {
        Fixture fixture = makeAttachment(CDN_URL, "https://MEDIA.discordapp.net:443/attachments/x.png?hm=s", 4,
            new byte[] {1, 2, 3, 4});

        assertThat(attachments.read(fixture.attachment(), MAX_BYTES)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void aDeclaredSizeOverTheCap_isTooLargeWithoutDownloading() {
        Fixture fixture = makeAttachment(CDN_URL, CDN_URL, (int) MAX_BYTES + 1, new byte[1]);

        assertThatThrownBy(() -> attachments.read(fixture.attachment(), MAX_BYTES))
            .isInstanceOf(AttachmentRejectedException.class)
            .extracting(e -> ((AttachmentRejectedException) e).reason()).isEqualTo(Reason.TOO_LARGE);
        verify(fixture.proxy(), never()).download();
    }

    @Test
    void aBodyOfOneByteOverTheCap_isTooLargeAndNotTruncated() {
        Fixture fixture = makeAttachment(CDN_URL, CDN_URL, 1, new byte[(int) MAX_BYTES + 1]);

        assertThatThrownBy(() -> attachments.read(fixture.attachment(), MAX_BYTES))
            .isInstanceOf(AttachmentRejectedException.class)
            .extracting(e -> ((AttachmentRejectedException) e).reason()).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void aBodyOfExactlyTheCap_isReturned() {
        byte[] body = new byte[(int) MAX_BYTES];
        body[0] = 7;
        Fixture fixture = makeAttachment(CDN_URL, CDN_URL, (int) MAX_BYTES, body);

        assertThat(attachments.read(fixture.attachment(), MAX_BYTES)).isEqualTo(body);
    }

    @Test
    void aFailedDownload_isReportedAsDownloadFailed() {
        Fixture fixture = makeAttachment(CDN_URL, CDN_URL, 4, null);
        when(fixture.proxy().download()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("x")));

        assertThatThrownBy(() -> attachments.read(fixture.attachment(), MAX_BYTES))
            .isInstanceOf(AttachmentRejectedException.class)
            .extracting(e -> ((AttachmentRejectedException) e).reason()).isEqualTo(Reason.DOWNLOAD_FAILED);
    }

    // AC4

    @Test
    void aRefusal_logsTheHostAtWarnButNeverTheSignedUrl() {
        Fixture fixture = makeAttachment("https://evil.example.org/a.md?hm=signature", CDN_URL, 4, new byte[4]);

        assertThatThrownBy(() -> attachments.read(fixture.attachment(), MAX_BYTES))
            .isInstanceOf(AttachmentRejectedException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("evil.example.org").doesNotContain("signature", "/a.md");
        });
    }

    private record Fixture(Message.Attachment attachment, NamedAttachmentProxy proxy) {}

    private static Fixture makeAttachment(String url, String proxyUrl, int declaredSize, byte[] body) {
        Message.Attachment attachment = mock(Message.Attachment.class);
        NamedAttachmentProxy proxy = mock(NamedAttachmentProxy.class);
        when(attachment.getUrl()).thenReturn(url);
        when(attachment.getSize()).thenReturn(declaredSize);
        when(attachment.getProxy()).thenReturn(proxy);
        when(proxy.getUrl()).thenReturn(proxyUrl);
        if (body != null) {
            when(proxy.download()).thenReturn(CompletableFuture.completedFuture(
                (InputStream) new ByteArrayInputStream(body)));
        }
        return new Fixture(attachment, proxy);
    }
}
