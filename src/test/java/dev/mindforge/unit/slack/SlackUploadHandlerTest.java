package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.slack.SlackAllowlist;
import dev.mindforge.slack.SlackFileRejectedException;
import dev.mindforge.slack.SlackFiles;
import dev.mindforge.slack.SlackKnowledgeBases;
import dev.mindforge.slack.SlackMessage;
import dev.mindforge.slack.SlackRateLimiter;
import dev.mindforge.slack.SlackUploadHandler;

class SlackUploadHandlerTest {

    private static final String TEAM = "T0HOME";
    private static final String FOREIGN_TEAM = "T0FOREIGN";
    private static final String SLACK_USER = "U0ADA";
    private static final String EXTERNAL_ID = TEAM + "-" + SLACK_USER;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FILE_URL = "https://files.slack.com/files-pri/T0HOME-F01/download/notes.md";
    private static final byte[] BODY = "# Osmosis\n".getBytes();
    private static final String NOT_AVAILABLE = "This bot is not available here.";
    private static final String NOT_ONE = "This command needs exactly one knowledge base. "
        + "Selecting a knowledge base is not available in Slack yet.";
    private static final String GRAMMAR = "The message text may hold only lesson_id=<value> and new_version.";
    private static final String SECRET = "internal detail sk-secret";

    private final SlackRateLimiter rateLimiter = mock(SlackRateLimiter.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final SlackFiles files = mock(SlackFiles.class);
    private final IngestionService ingestion = mock(IngestionService.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final RecordingSlackReply reply = new RecordingSlackReply();
    private final SlackUploadHandler handler = makeHandler(Runnable::run);

    {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(OptionalLong.empty());
        when(identities.resolve(ExternalPlatform.SLACK, EXTERNAL_ID, null)).thenReturn(USER_ID);
        when(files.read(anyString(), anyLong())).thenReturn(BODY);
    }

    // AC2

    @Test
    void aFileSharedInAChannel_isIgnoredWithoutAReply() {
        handler.handle(makeMessage("channel", "file_share", null, null, makeFile()), reply);

        assertNothingHappened();
    }

    @Test
    void aBotMessageWithAFile_isIgnoredWithoutAReply() {
        handler.handle(makeMessage("im", "file_share", "B0BOT", null, makeFile()), reply);

        assertNothingHappened();
    }

    @Test
    void aDirectMessageWithoutAFile_isIgnoredWithoutAReply() {
        handler.handle(makeMessage("im", null, null, "hello"), reply);
        handler.handle(makeMessage("im", "message_changed", null, null), reply);

        assertNothingHappened();
    }

    @Test
    void anUpload_isAcknowledgedBeforeTheGateRunsOnTheExecutor() {
        makeOnlyKnowledgeBase();
        SlackUploadHandler deferred = makeHandler(queued::add);

        deferred.handle(makeUpload(null, makeFile()), reply);

        assertThat(queued).hasSize(1);
        verifyNoInteractions(rateLimiter, identities, files, ingestion);
        queued.getFirst().run();
        assertThat(reply.events).singleElement().asString().contains("queued");
    }

    // AC3

    @Test
    void anUnlistedWorkspaceOrCallerTeam_isRefusedBeforeAnythingElse() {
        handler.handle(makeMessage(FOREIGN_TEAM, TEAM, "im", "file_share", null, null, List.of(makeFile())), reply);
        handler.handle(makeMessage(TEAM, FOREIGN_TEAM, "im", "file_share", null, null, List.of(makeFile())), reply);
        handler.handle(makeMessage(TEAM, null, "im", "file_share", null, null, List.of(makeFile())), reply);

        assertThat(reply.events).containsExactly(NOT_AVAILABLE, NOT_AVAILABLE, NOT_AVAILABLE);
        verifyNoInteractions(rateLimiter, identities, knowledgeBaseService, files, ingestion);
    }

    // AC4

    @Test
    void overTheRateLimit_isRefusedAndNothingIsResolvedOrDownloaded() {
        when(rateLimiter.tryAcquire(SLACK_USER)).thenReturn(OptionalLong.of(42));

        handler.handle(makeUpload(null, makeFile()), reply);

        assertThat(reply.events).containsExactly("Rate limit reached. Try again in 42 s.");
        verifyNoInteractions(identities, knowledgeBaseService, files, ingestion);
    }

    @Test
    void anUpload_countsOnceOnTheRateLimit_beforeResolutionAndDownload() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        when(ingestion.ingest(any())).thenReturn(UUID.randomUUID());

        handler.handle(makeUpload(null, makeFile()), reply);

        InOrder order = inOrder(rateLimiter, identities, knowledgeBaseService, files, ingestion);
        order.verify(rateLimiter, times(1)).tryAcquire(SLACK_USER);
        order.verify(identities).resolve(ExternalPlatform.SLACK, EXTERNAL_ID, null);
        order.verify(knowledgeBaseService).requireOwner(kb.kbId(), USER_ID);
        order.verify(files).read(FILE_URL, BODY.length);
        order.verify(ingestion).ingest(any());
    }

    // AC5

    @Test
    void zeroOrSeveralKnowledgeBases_giveTheFixedRefusalAndNothingIsDownloaded() {
        when(knowledgeBaseService.list(USER_ID)).thenReturn(List.of())
            .thenReturn(List.of(makeKnowledgeBase(), makeKnowledgeBase()));

        handler.handle(makeUpload(null, makeFile()), reply);
        handler.handle(makeUpload(null, makeFile()), reply);

        assertThat(reply.events).containsExactly(NOT_ONE, NOT_ONE);
        verifyNoInteractions(files, ingestion);
    }

    @Test
    void aForeignKnowledgeBase_givesTheFixedRefusalAndNothingIsDownloaded() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        doThrow(new NotOwnerException(kb.kbId())).when(knowledgeBaseService).requireOwner(kb.kbId(), USER_ID);

        handler.handle(makeUpload(null, makeFile()), reply);

        assertThat(reply.events).containsExactly(NOT_ONE);
        verifyNoInteractions(files, ingestion);
    }

    // AC6

    @Test
    void twoFiles_areRefusedAndNothingIsResolvedOrDownloaded() {
        handler.handle(makeUpload(null, makeFile(), makeFile()), reply);

        assertThat(reply.events).containsExactly("Send one file per message.");
        verifyNoInteractions(identities, files, ingestion);
    }

    @Test
    void aTextOutsideTheGrammar_isRefusedAndNothingIsResolvedOrDownloaded() {
        handler.handle(makeUpload("please upload", makeFile()), reply);
        handler.handle(makeUpload("lesson_id=", makeFile()), reply);

        assertThat(reply.events).containsExactly(GRAMMAR, GRAMMAR);
        verifyNoInteractions(identities, files, ingestion);
    }

    // AC6, AC7

    @Test
    void aValidUpload_ingestsOnceAsSlackWithTheTextOptionsAndRepliesThatTheRunIsQueued() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        UUID documentId = UUID.randomUUID();
        when(ingestion.ingest(any())).thenReturn(documentId);

        handler.handle(makeUpload("LESSON_ID=osmosis New_Version", makeFile()), reply);

        ArgumentCaptor<DocumentUpload> upload = ArgumentCaptor.forClass(DocumentUpload.class);
        verify(ingestion).ingest(upload.capture());
        assertThat(upload.getValue())
            .extracting(DocumentUpload::knowledgeBaseId, DocumentUpload::uploadedBy, DocumentUpload::source,
                DocumentUpload::filename, DocumentUpload::mimeType, DocumentUpload::content,
                DocumentUpload::lessonId, DocumentUpload::newVersion)
            .containsExactly(kb.kbId(), USER_ID, UploadSource.SLACK, "notes.md", "text/markdown", BODY,
                "osmosis", true);
        assertThat(reply.events).singleElement().asString()
            .contains("Document " + documentId).contains("queued").contains("identical");
    }

    @Test
    void aMimeTypeWithParametersAndNoText_ingestsWithTheBareMediaTypeAndNoOptions() {
        makeOnlyKnowledgeBase();
        when(ingestion.ingest(any())).thenReturn(UUID.randomUUID());

        handler.handle(makeUpload(null,
            new SlackMessage.File("notes.md", "Text/Markdown ; charset=utf-8", BODY.length, FILE_URL)), reply);

        ArgumentCaptor<DocumentUpload> upload = ArgumentCaptor.forClass(DocumentUpload.class);
        verify(ingestion).ingest(upload.capture());
        assertThat(upload.getValue().mimeType()).isEqualTo("text/markdown");
        assertThat(upload.getValue().lessonId()).isNull();
        assertThat(upload.getValue().newVersion()).isFalse();
    }

    // AC8

    static Stream<Arguments> failures() {
        return Stream.of(
            Arguments.of(new SlackFileRejectedException(SlackFileRejectedException.Reason.TOO_LARGE),
                "The file is larger than the upload limit."),
            Arguments.of(new SlackFileRejectedException(SlackFileRejectedException.Reason.NOT_FROM_SLACK),
                "The file is not from Slack's file host and was refused."),
            Arguments.of(new SlackFileRejectedException(SlackFileRejectedException.Reason.DOWNLOAD_FAILED),
                "The file could not be downloaded. Try again later."),
            Arguments.of(new LessonAlreadyExistsException(new LessonIdentity("osmosis", SECRET)),
                "Lesson exists; send new_version or another lesson_id."),
            Arguments.of(new UnknownLessonException(SECRET),
                "No lesson has this lesson_id; send it without new_version."),
            Arguments.of(new LessonIdentityException(SECRET), "No valid lesson id; send a valid lesson_id."),
            Arguments.of(new UploadRejectedException(SECRET), "The file was refused; check its type, name and size."),
            Arguments.of(new KnowledgeBaseBusyException(UUID.randomUUID()),
                "The knowledge base is busy. Try again later."),
            Arguments.of(new IllegalStateException(SECRET), "Something went wrong. Try again later."));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void aFailure_givesItsFixedReplyWithoutTheMessage(RuntimeException failure, String expected) {
        makeOnlyKnowledgeBase();
        if (failure instanceof SlackFileRejectedException) {
            when(files.read(anyString(), anyLong())).thenThrow(failure);
        } else {
            when(ingestion.ingest(any())).thenThrow(failure);
        }

        handler.handle(makeUpload(null, makeFile()), reply);

        assertThat(reply.events).containsExactly(expected);
        assertThat(reply.events.getFirst()).doesNotContain(SECRET);
    }

    // AC9

    @Test
    void aRedeliveredEvent_getsTheSameDocumentAndOneReplyPerEvent() {
        makeOnlyKnowledgeBase();
        UUID documentId = UUID.randomUUID();
        when(ingestion.ingest(any())).thenReturn(documentId);
        SlackMessage event = makeUpload(null, makeFile());

        handler.handle(event, reply);
        handler.handle(event, reply);

        assertThat(reply.events).hasSize(2)
            .allSatisfy(text -> assertThat(text).contains("Document " + documentId).contains("queued"));
    }

    private SlackUploadHandler makeHandler(Executor executor) {
        return new SlackUploadHandler(new SlackAllowlist(Set.of(TEAM)), rateLimiter, identities,
            new SlackKnowledgeBases(knowledgeBaseService), files, ingestion, executor);
    }

    private void assertNothingHappened() {
        assertThat(reply.events).isEmpty();
        verifyNoInteractions(rateLimiter, identities, knowledgeBaseService, files, ingestion);
    }

    private KnowledgeBase makeOnlyKnowledgeBase() {
        KnowledgeBase kb = makeKnowledgeBase();
        when(knowledgeBaseService.list(USER_ID)).thenReturn(List.of(kb));
        return kb;
    }

    private static KnowledgeBase makeKnowledgeBase() {
        return new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
    }

    private static SlackMessage.File makeFile() {
        return new SlackMessage.File("notes.md", "text/markdown", BODY.length, FILE_URL);
    }

    private static SlackMessage makeUpload(String text, SlackMessage.File... files) {
        return makeMessage("im", "file_share", null, text, files);
    }

    private static SlackMessage makeMessage(String channelType, String subtype, String botId, String text,
                                            SlackMessage.File... files) {
        return makeMessage(TEAM, TEAM, channelType, subtype, botId, text, List.of(files));
    }

    private static SlackMessage makeMessage(String workspaceTeam, String callerTeam, String channelType,
                                            String subtype, String botId, String text, List<SlackMessage.File> files) {
        return new SlackMessage(workspaceTeam, callerTeam, SLACK_USER, "D0DM", channelType, subtype, botId, text,
            files);
    }
}
