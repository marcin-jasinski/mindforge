package dev.mindforge.discord;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.NamedAttachmentProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.discord.AttachmentRejectedException.Reason;

/**
 * The only class that downloads a Discord attachment (ADR 0029). Discord's CDN is the operator-configured platform,
 * so JDA's own client fetches it, but only from a fixed host list over {@code https} on the default port, and only to
 * the upload size limit: a larger file fails and is never truncated.
 */
public final class DiscordAttachments {

    private static final Logger log = LoggerFactory.getLogger(DiscordAttachments.class);

    private static final Set<String> CDN_HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 60;
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * @return the attachment's bytes, at most {@code maxBytes}
     * @throws AttachmentRejectedException when a URL is not on Discord's CDN, the file is over {@code maxBytes}, or
     *         the download fails
     */
    public byte[] read(Message.Attachment attachment, long maxBytes) {
        NamedAttachmentProxy proxy = attachment.getProxy();
        requireCdn(attachment.getUrl());
        requireCdn(proxy.getUrl());
        if (attachment.getSize() > maxBytes) {
            throw new AttachmentRejectedException(Reason.TOO_LARGE);
        }
        CompletableFuture<InputStream> download = proxy.download();
        byte[] content;
        try (InputStream in = download.get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            content = in.readNBytes((int) Math.min(MAX_ARRAY_LENGTH, maxBytes + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw downloadFailed(e);
        } catch (ExecutionException | TimeoutException | IOException e) {
            download.cancel(true);
            throw downloadFailed(e);
        }
        if (content.length > maxBytes) {
            throw new AttachmentRejectedException(Reason.TOO_LARGE);
        }
        return content;
    }

    /** Logs the host only: the URL's query is a signature. */
    private static void requireCdn(String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url);
        } catch (URISyntaxException e) {
            log.warn("Discord attachment refused: URL does not parse");
            throw new AttachmentRejectedException(Reason.NOT_FROM_DISCORD, e);
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        boolean admitted = "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443) && host != null && CDN_HOSTS.contains(host);
        if (!admitted) {
            log.warn("Discord attachment refused: host={}", host);
            throw new AttachmentRejectedException(Reason.NOT_FROM_DISCORD);
        }
    }

    private static AttachmentRejectedException downloadFailed(Exception e) {
        log.warn("Discord attachment download failed: error={}", e.getClass().getName());
        return new AttachmentRejectedException(Reason.DOWNLOAD_FAILED, e);
    }
}
