package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.RetryNotAllowedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.DocumentParser;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.UploadPolicy;
import dev.mindforge.support.TestFixtures;

class IngestionServiceTest {

    private static final UUID KB = UUID.randomUUID();
    private static final UUID OTHER_KB = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String CONTENT = "Mitoza to podział komórki.";
    private static final ContentHash HASH = ContentHash.compute(CONTENT.getBytes(StandardCharsets.UTF_8));

    /** Reads the bytes as one text block, the way a real parser would for plain text. */
    private static final DocumentParser PARSER = (mimeType, content) -> {
        String text = new String(content, StandardCharsets.UTF_8);
        return new ParsedDocument(text, List.of(ContentBlock.text(text, 0)), Map.of());
    };

    private final DocumentRepository documents = makeDocuments();
    private final IngestRunRepository runs = makeRuns();
    private final EventPublisher events = mock(EventPublisher.class);
    private final ProgressNotifier progress = mock(ProgressNotifier.class);

    @Test
    void shouldInsertANewLessonWithAQueuedRunUnderTheKnowledgeBaseLockAndNotifyAfterwards() {
        UUID documentId = makeService(null).ingest(makeUpload(KB, null, false));

        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IngestRun> enqueued = ArgumentCaptor.forClass(IngestRun.class);
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        InOrder order = inOrder(documents, runs, events, progress);
        order.verify(documents).lockKnowledgeBase(KB);
        order.verify(documents).findByContentHash(KB, HASH);
        order.verify(documents).insert(eq(KB), inserted.capture());
        order.verify(runs).enqueue(eq(KB), enqueued.capture());
        order.verify(events).publish(published.capture());
        order.verify(progress).notify(eq(KB), any());

        assertThat(inserted.getValue()).usingRecursiveComparison().isEqualTo(new Document(
            documentId, KB, new LessonIdentity("notatki", "notatki"), HASH, "notatki.md", "text/markdown",
            CONTENT, List.of(ContentBlock.text(CONTENT, 0)), UploadSource.API, USER, null, null));
        assertThat(enqueued.getValue()).extracting(IngestRun::kind, IngestRun::documentId, IngestRun::status)
            .containsExactly(RunKind.INGEST, documentId, RunStatus.QUEUED);
        assertThat(published.getValue()).usingRecursiveComparison().ignoringFields("occurredAt")
            .isEqualTo(new DomainEvent.IngestRunQueued(enqueued.getValue().runId(), KB, null));
    }

    @Test
    void shouldReturnTheExistingDocumentForAnIdenticalUpload() {
        Document existing = TestFixtures.makeDocument(null, KB);
        when(documents.findByContentHash(KB, HASH)).thenReturn(Optional.of(existing));

        UUID documentId = makeService(null).ingest(makeUpload(KB, null, false));

        assertThat(documentId).isEqualTo(existing.documentId());
        verify(documents, never()).insert(any(), any());
        verifyNoInteractions(runs, events, progress);
    }

    @Test
    void shouldCreateANewDocumentWhenTheSameBytesAreUploadedIntoAnotherKnowledgeBase() {
        Document existing = TestFixtures.makeDocument(null, KB);
        when(documents.findByContentHash(KB, HASH)).thenReturn(Optional.of(existing));

        UUID documentId = makeService(null).ingest(makeUpload(OTHER_KB, null, false));

        assertThat(documentId).isNotEqualTo(existing.documentId());
        verify(documents).insert(eq(OTHER_KB), any());
    }

    @Test
    void shouldRejectAnUploadWhoseLessonAlreadyExistsNamingTheExistingLesson() {
        when(documents.findLessonTitle(KB, "notatki")).thenReturn(Optional.of("Biologia — lekcja 3"));

        assertThatThrownBy(() -> makeService(null).ingest(makeUpload(KB, null, false)))
            .isInstanceOfSatisfying(LessonAlreadyExistsException.class, rejection ->
                assertThat(rejection.lesson()).isEqualTo(new LessonIdentity("notatki", "Biologia — lekcja 3")));
        verify(documents, never()).insert(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void shouldInsertASecondDocumentOfAnExistingLessonAsANewVersion() {
        when(documents.findLessonTitle(KB, "notatki")).thenReturn(Optional.of("notatki"));

        UUID documentId = makeService(null).ingest(makeUpload(KB, null, true));

        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(documents).insert(eq(KB), inserted.capture());
        assertThat(inserted.getValue().documentId()).isEqualTo(documentId);
        assertThat(inserted.getValue().lessonIdentity().lessonId()).isEqualTo("notatki");
        verify(events).publish(any());
    }

    @Test
    void shouldRejectANewVersionOfALessonThatDoesNotExist() {
        assertThatExceptionOfType(UnknownLessonException.class)
            .isThrownBy(() -> makeService(null).ingest(makeUpload(KB, null, true)))
            .withMessageContaining("notatki");
        verify(documents, never()).insert(any(), any());
    }

    @Test
    void shouldUseTheLessonIdOverrideAndKeepTheResolvedTitle() {
        makeService(null).ingest(makeUpload(KB, "bio-3", false));

        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(documents).insert(eq(KB), inserted.capture());
        assertThat(inserted.getValue().lessonIdentity()).isEqualTo(new LessonIdentity("bio-3", "notatki"));
    }

    @Test
    void shouldRejectALessonIdOverrideOutsideTheIdentifierGrammar() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> makeService(null).ingest(makeUpload(KB, "Bio 3", false)));
        verify(documents, never()).insert(any(), any());
    }

    @Test
    void shouldRejectAnUploadTheUploadPolicyRefusesBeforeTouchingTheKnowledgeBase() {
        UploadPolicy refuseAll = (filename, mimeType, sizeBytes) -> {
            throw new UploadRejectedException("too large");
        };

        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> makeService(refuseAll).ingest(makeUpload(KB, null, false)));
        verifyNoInteractions(documents, events);
    }

    @Test
    void shouldRetryADocumentOnlyWhenItsLatestRunFailed() {
        UUID documentId = UUID.randomUUID();
        when(runs.latestForDocument(KB, documentId)).thenReturn(Optional.of(makeRun(documentId, RunStatus.FAILED)));

        UUID runId = makeService(null).retry(KB, documentId);

        ArgumentCaptor<IngestRun> enqueued = ArgumentCaptor.forClass(IngestRun.class);
        InOrder order = inOrder(documents, runs, events, progress);
        order.verify(documents).lockKnowledgeBase(KB);
        order.verify(runs).enqueue(eq(KB), enqueued.capture());
        order.verify(events).publish(any());
        order.verify(progress).notify(eq(KB), any());
        assertThat(enqueued.getValue()).extracting(IngestRun::runId, IngestRun::documentId, IngestRun::attempt)
            .containsExactly(runId, documentId, 1);
    }

    @Test
    void shouldRefuseToRetryADocumentWhoseLatestRunIsQueuedOrCompleted() {
        UUID queued = UUID.randomUUID();
        UUID completed = UUID.randomUUID();
        when(runs.latestForDocument(KB, queued)).thenReturn(Optional.of(makeRun(queued, RunStatus.QUEUED)));
        when(runs.latestForDocument(KB, completed)).thenReturn(Optional.of(makeRun(completed, RunStatus.COMPLETED)));

        assertThatExceptionOfType(RetryNotAllowedException.class).isThrownBy(() -> makeService(null).retry(KB, queued));
        assertThatExceptionOfType(RetryNotAllowedException.class)
            .isThrownBy(() -> makeService(null).retry(KB, completed));
        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> makeService(null).retry(KB, UUID.randomUUID()));
        verify(runs, never()).enqueue(any(), any());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static IngestRun makeRun(UUID documentId, RunStatus status) {
        return new IngestRun(UUID.randomUUID(), KB, RunKind.INGEST, documentId, null, status, 2, null, null,
            List.of(), false, 0, Map.of(), List.of(), null, null, null);
    }

    private IngestionService makeService(UploadPolicy policy) {
        return new IngestionService(
            policy != null ? policy : (filename, mimeType, sizeBytes) -> filename,
            PARSER, documents, runs, events, progress, TransactionOperations.withoutTransaction());
    }

    private static IngestRunRepository makeRuns() {
        IngestRunRepository runs = mock(IngestRunRepository.class);
        when(runs.enqueue(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        return runs;
    }

    private static DocumentRepository makeDocuments() {
        DocumentRepository documents = mock(DocumentRepository.class);
        when(documents.insert(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        return documents;
    }

    private static DocumentUpload makeUpload(UUID knowledgeBaseId, String lessonId, boolean newVersion) {
        return new DocumentUpload(knowledgeBaseId, USER, UploadSource.API, "notatki.md", "text/markdown",
            CONTENT.getBytes(StandardCharsets.UTF_8), lessonId, newVersion);
    }
}
