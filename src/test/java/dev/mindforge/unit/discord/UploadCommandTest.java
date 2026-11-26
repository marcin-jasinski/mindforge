package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.discord.DiscordAllowlist;
import dev.mindforge.discord.DiscordAttachments;
import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordCommandHandler;
import dev.mindforge.discord.DiscordKnowledgeBases;
import dev.mindforge.discord.DiscordRateLimiter;
import dev.mindforge.discord.UploadCommand;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.NamedAttachmentProxy;

class UploadCommandTest {

    private static final long GUILD = 100L;
    private static final long DISCORD_USER = 300L;
    private static final long MAX_BYTES = 64;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String CDN_URL = "https://cdn.discordapp.com/attachments/1/2/notes.md?hm=signature";
    private static final byte[] BODY = "# Osmosis\n".getBytes();
    private static final String UNKNOWN = "Unknown knowledge base.";
    private static final String SECRET = "internal detail sk-secret";

    private final IngestionService ingestion = mock(IngestionService.class);
    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final DiscordKnowledgeBases knowledgeBases = new DiscordKnowledgeBases(
        new DiscordAllowlist(Set.of(GUILD), Set.of()), identities,
            new KnowledgeBaseService(repository, mock(QuizSessionStore.class)));
    private final UploadCommand command =
        new UploadCommand(ingestion, knowledgeBases, new DiscordAttachments(), MAX_BYTES);
    private final RecordingDiscordReply reply = new RecordingDiscordReply();

    // AC1

    @Test
    void definition_hasKbFileLessonIdAndNewVersionOptions() {
        assertThat(command.definition().getName()).isEqualTo("upload");
        assertThat(command.definition().getOptions())
            .extracting(OptionData::getName, OptionData::getType, OptionData::isRequired, OptionData::isAutoComplete)
            .containsExactly(
                tuple("kb", OptionType.STRING, true, true),
                tuple("file", OptionType.ATTACHMENT, true, false),
                tuple("lesson_id", OptionType.STRING, false, false),
                tuple("new_version", OptionType.BOOLEAN, false, false));
    }

    @Test
    void uploadCommand_isTheOnlyProducerOfTheDiscordSource() throws IOException {
        Path main = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(main)) {
            List<String> users = files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> read(path).contains("UploadSource.DISCORD"))
                .map(path -> main.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .toList();
            // ArticleFetcher only reads the source to decide whether to scan a document.
            assertThat(users).containsExactly("dev/mindforge/application/service/ArticleFetcher.java",
                "dev/mindforge/discord/UploadCommand.java");
        }
    }

    // AC2

    @Test
    void anOwnedKnowledgeBaseAndACdnAttachment_ingestOnceAsDiscordAndReplyThatTheRunIsQueued() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        UUID documentId = UUID.randomUUID();
        when(ingestion.ingest(any())).thenReturn(documentId);

        command.handle(makeCall(kb.kbId().toString(), makeAttachment(CDN_URL, BODY.length).attachment(),
            Map.of("lesson_id", "osmosis", "new_version", "true")), USER_ID, reply);

        ArgumentCaptor<DocumentUpload> upload = ArgumentCaptor.forClass(DocumentUpload.class);
        verify(ingestion).ingest(upload.capture());
        assertThat(upload.getValue())
            .extracting(DocumentUpload::knowledgeBaseId, DocumentUpload::uploadedBy, DocumentUpload::source,
                DocumentUpload::filename, DocumentUpload::mimeType, DocumentUpload::content,
                DocumentUpload::lessonId, DocumentUpload::newVersion)
            .containsExactly(kb.kbId(), USER_ID, UploadSource.DISCORD, "notes.md", "text/markdown", BODY,
                "osmosis", true);
        assertThat(reply.events).singleElement().asString()
            .contains("Document " + documentId).contains("queued").contains("identical");
    }

    @Test
    void aContentTypeWithACharsetParameter_ingestsWithTheBareMediaType() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        when(ingestion.ingest(any())).thenReturn(UUID.randomUUID());
        Message.Attachment attachment = makeAttachment(CDN_URL, BODY.length).attachment();
        when(attachment.getContentType()).thenReturn("Text/Markdown ; charset=utf-8");

        command.handle(makeCall(kb.kbId().toString(), attachment, Map.of()), USER_ID, reply);

        ArgumentCaptor<DocumentUpload> upload = ArgumentCaptor.forClass(DocumentUpload.class);
        verify(ingestion).ingest(upload.capture());
        assertThat(upload.getValue().mimeType()).isEqualTo("text/markdown");
    }

    @Test
    void withoutLessonIdAndNewVersion_ingestsWithNoOverrideAndNotANewVersion() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        when(ingestion.ingest(any())).thenReturn(UUID.randomUUID());

        command.handle(makeCall(kb.kbId().toString(), makeAttachment(CDN_URL, BODY.length).attachment(), Map.of()),
            USER_ID, reply);

        ArgumentCaptor<DocumentUpload> upload = ArgumentCaptor.forClass(DocumentUpload.class);
        verify(ingestion).ingest(upload.capture());
        assertThat(upload.getValue().lessonId()).isNull();
        assertThat(upload.getValue().newVersion()).isFalse();
    }

    // AC4

    static Stream<Arguments> ingestFailures() {
        return Stream.of(
            Arguments.of(new LessonAlreadyExistsException(new LessonIdentity("osmosis", SECRET)),
                "Lesson exists; send new_version or another lesson_id."),
            Arguments.of(new UnknownLessonException(SECRET),
                "No lesson has this lesson_id; send it without new_version."),
            Arguments.of(new LessonIdentityException(SECRET), "No valid lesson id; send a valid lesson_id."),
            Arguments.of(new UploadRejectedException(SECRET), "The file was refused; check its type, name and size."),
            Arguments.of(new KnowledgeBaseBusyException(UUID.randomUUID()),
                "The knowledge base is busy. Try again later."));
    }

    @ParameterizedTest
    @MethodSource("ingestFailures")
    void anIngestFailure_givesItsFixedReplyWithoutTheMessage(RuntimeException failure, String expected) {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        when(ingestion.ingest(any())).thenThrow(failure);

        command.handle(makeCall(kb.kbId().toString(), makeAttachment(CDN_URL, BODY.length).attachment(), Map.of()),
            USER_ID, reply);

        assertThat(reply.events).containsExactly(expected);
        assertThat(reply.events.getFirst()).doesNotContain(SECRET);
    }

    @Test
    void anAttachmentOffTheCdn_givesTheFixedReplyAndNothingIsDownloadedOrIngested() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Fixture fixture = makeAttachment("https://evil.example.org/notes.md?hm=signature", BODY.length);

        command.handle(makeCall(kb.kbId().toString(), fixture.attachment(), Map.of()), USER_ID, reply);

        assertThat(reply.events).containsExactly("The attachment is not from Discord's CDN and was refused.");
        verify(fixture.proxy(), never()).download();
        verifyNoInteractions(ingestion);
    }

    @Test
    void anAttachmentOverTheLimit_givesTheFixedReplyAndNothingIsDownloadedOrIngested() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Fixture fixture = makeAttachment(CDN_URL, (int) MAX_BYTES + 1);

        command.handle(makeCall(kb.kbId().toString(), fixture.attachment(), Map.of()), USER_ID, reply);

        assertThat(reply.events).containsExactly("The file is larger than the upload limit.");
        verify(fixture.proxy(), never()).download();
        verifyNoInteractions(ingestion);
    }

    // AC5

    @Test
    void aKbValueThatIsNotAUuid_givesTheFixedReplyAndNothingIsDownloadedOrIngested() {
        Fixture fixture = makeAttachment(CDN_URL, BODY.length);

        command.handle(makeCall("Biology", fixture.attachment(), Map.of()), USER_ID, reply);

        assertThat(reply.events).containsExactly(UNKNOWN);
        verifyNoInteractions(fixture.attachment(), ingestion);
    }

    @Test
    void anotherUsersKnowledgeBase_givesTheFixedReplyAndNothingIsDownloadedOrIngested() {
        KnowledgeBase foreign = new KnowledgeBase(UUID.randomUUID(), UUID.randomUUID(), "Theirs", null, null, 0, 0);
        when(repository.findById(foreign.kbId())).thenReturn(Optional.of(foreign));
        Fixture fixture = makeAttachment(CDN_URL, BODY.length);

        command.handle(makeCall(foreign.kbId().toString(), fixture.attachment(), Map.of()), USER_ID, reply);

        assertThat(reply.events).containsExactly(UNKNOWN);
        verifyNoInteractions(fixture.attachment(), ingestion);
    }

    // AC8

    @Test
    void throughTheGate_theReplyIsDeferredThroughTheEphemeralSeamBeforeTheDownload() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        UUID documentId = UUID.randomUUID();
        when(ingestion.ingest(any())).thenReturn(documentId);
        DiscordRateLimiter rateLimiter = mock(DiscordRateLimiter.class);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(OptionalLong.empty());
        when(identities.resolve(ExternalPlatform.DISCORD, "300", "Ada")).thenReturn(USER_ID);
        DiscordCommandHandler handler = new DiscordCommandHandler(new DiscordAllowlist(Set.of(GUILD), Set.of()),
            rateLimiter, identities, List.of(command));

        handler.handle(makeCall(kb.kbId().toString(), makeAttachment(CDN_URL, BODY.length).attachment(), Map.of()),
            reply);

        assertThat(reply.events).hasSize(2);
        assertThat(reply.events.getFirst()).isEqualTo(RecordingDiscordReply.DEFER);
        assertThat(reply.events.get(1)).contains("Document " + documentId);
        verify(rateLimiter).tryAcquire(DISCORD_USER);
    }

    private KnowledgeBase makeOwnedKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
        when(repository.findById(kb.kbId())).thenReturn(Optional.of(kb));
        return kb;
    }

    private record Fixture(Message.Attachment attachment, NamedAttachmentProxy proxy) {}

    private static Fixture makeAttachment(String url, int declaredSize) {
        Message.Attachment attachment = mock(Message.Attachment.class);
        NamedAttachmentProxy proxy = mock(NamedAttachmentProxy.class);
        when(attachment.getUrl()).thenReturn(url);
        when(attachment.getSize()).thenReturn(declaredSize);
        when(attachment.getFileName()).thenReturn("notes.md");
        when(attachment.getContentType()).thenReturn("text/markdown");
        when(attachment.getProxy()).thenReturn(proxy);
        when(proxy.getUrl()).thenReturn(url);
        when(proxy.download()).thenReturn(CompletableFuture.completedFuture(
            (InputStream) new ByteArrayInputStream(BODY)));
        return new Fixture(attachment, proxy);
    }

    private static DiscordCall makeCall(String kbOption, Message.Attachment attachment, Map<String, String> extra) {
        Map<String, String> options = new HashMap<>(extra);
        options.put("kb", kbOption);
        options.put("file", "1");
        return new DiscordCall(GUILD, Set.of(), DISCORD_USER, "Ada", "upload", null, options,
            Map.of("file", attachment));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
