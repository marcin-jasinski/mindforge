package dev.mindforge.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.ConversationTurn;
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
import dev.mindforge.domain.model.RunProgress;
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

/**
 * Accepts an upload into a knowledge base. The upload is admitted, parsed and given a lesson identity outside
 * any transaction; then one transaction, opened by the knowledge base's row lock so uploads into one knowledge
 * base serialize, returns an identical document, applies the lesson rule, or inserts a new {@link Document}
 * together with its {@code QUEUED} ingest run and {@link DomainEvent.IngestRunQueued}.
 */
public class IngestionService {

    private static final LessonIdentity CONVERSATION_LESSON = new LessonIdentity("conversation", "Conversation");
    private static final String CONVERSATION_FILENAME = "conversation";
    private static final String CONVERSATION_MIME_TYPE = "text/plain";

    private final UploadPolicy uploadPolicy;
    private final DocumentParser parser;
    private final DocumentRepository documents;
    private final IngestRunRepository runs;
    private final EventPublisher events;
    private final ProgressNotifier progress;
    private final TransactionOperations transactions;

    public IngestionService(UploadPolicy uploadPolicy, DocumentParser parser, DocumentRepository documents,
                            IngestRunRepository runs, EventPublisher events, ProgressNotifier progress,
                            TransactionOperations transactions) {
        this.uploadPolicy = uploadPolicy;
        this.parser = parser;
        this.documents = documents;
        this.runs = runs;
        this.events = events;
        this.progress = progress;
        this.transactions = transactions;
    }

    /**
     * @return the id of the inserted document, or of the identical document already in the knowledge base
     * @throws UploadRejectedException when the upload may not be ingested or cannot be read
     * @throws LessonIdentityException when no lesson identity resolves, or the {@code lessonId} override is invalid
     * @throws LessonAlreadyExistsException when the lesson has a document and the upload is not a new version
     * @throws UnknownLessonException when a new version names a lesson with no document
     */
    public UUID ingest(DocumentUpload upload) {
        UUID kbId = upload.knowledgeBaseId();
        String filename = uploadPolicy.admit(upload.filename(), upload.mimeType(), upload.content().length);
        ParsedDocument parsed = parser.parse(upload.mimeType(), upload.content());
        LessonIdentity lesson = LessonIdentity.resolve(withLessonId(parsed.metadata(), upload.lessonId()), filename);
        ContentHash hash = ContentHash.compute(upload.content());

        Accepted accepted = transactions.execute(status -> {
            documents.lockKnowledgeBase(kbId);
            Optional<Document> identical = documents.findByContentHash(kbId, hash);
            if (identical.isPresent()) {
                return new Accepted(identical.get().documentId(), null);
            }
            Optional<String> existingTitle = documents.findLessonTitle(kbId, lesson.lessonId());
            if (existingTitle.isPresent() && !upload.newVersion()) {
                throw new LessonAlreadyExistsException(new LessonIdentity(lesson.lessonId(), existingTitle.get()));
            }
            if (existingTitle.isEmpty() && upload.newVersion()) {
                throw new UnknownLessonException(lesson.lessonId());
            }

            Document document = documents.insert(kbId, new Document(
                UUID.randomUUID(), kbId, lesson, hash, filename, upload.mimeType(), parsed.text(), parsed.blocks(),
                upload.source(), upload.uploadedBy(), null, null));
            return new Accepted(document.documentId(), enqueue(kbId, document.documentId()));
        });
        if (accepted.run() != null) {
            progress.notify(kbId, RunProgress.status(accepted.run(), RunStatus.QUEUED));
        }
        return accepted.documentId();
    }

    /**
     * Queues a conversation edit exactly as an upload (T15): a {@code CONVERSATION} document in the reserved
     * {@code conversation} lesson — never deduplicated and outside the lesson rule — with its {@code QUEUED} run.
     *
     * @param quotedAnswer the answer a "save that" quotes, or null
     * @return the id of the queued run
     */
    public UUID submitEdit(UUID kbId, UUID userId, String instruction, String quotedAnswer) {
        String content = new ConversationTurn(instruction, quotedAnswer).content();
        IngestRun run = transactions.execute(status -> {
            documents.lockKnowledgeBase(kbId);
            Document turn = documents.insert(kbId, new Document(UUID.randomUUID(), kbId, CONVERSATION_LESSON,
                ContentHash.compute(content.getBytes(StandardCharsets.UTF_8)), CONVERSATION_FILENAME,
                CONVERSATION_MIME_TYPE, content, List.of(ContentBlock.text(content, 0)), UploadSource.CONVERSATION,
                userId, null, null));
            return enqueue(kbId, turn.documentId());
        });
        progress.notify(kbId, RunProgress.status(run, RunStatus.QUEUED));
        return run.runId();
    }

    /** An uploaded document and its newest run, null before one exists. */
    public record DocumentState(Document document, IngestRun latestRun) {}

    /** The knowledge base's uploads, newest first; conversation turns are not documents a learner manages. */
    public List<DocumentState> documents(UUID kbId) {
        Map<UUID, IngestRun> latest = runs.latestPerDocument(kbId).stream()
            .collect(Collectors.toMap(IngestRun::documentId, Function.identity()));
        return documents.listByKnowledgeBase(kbId).stream()
            .filter(document -> document.uploadSource() != UploadSource.CONVERSATION)
            .sorted(Comparator.comparing(Document::createdAt).reversed())
            .map(document -> new DocumentState(document, latest.get(document.documentId())))
            .toList();
    }

    /** @throws NotFoundException when the knowledge base has no such document */
    public DocumentState document(UUID kbId, UUID documentId) {
        Document document = documents.findById(kbId, documentId).orElseThrow(() -> new NotFoundException("Document"));
        return new DocumentState(document, runs.latestForDocument(kbId, documentId).orElse(null));
    }

    /**
     * Queues a new first-attempt ingest of a document whose latest run failed, against the current wiki.
     *
     * @return the id of the queued run
     * @throws NotFoundException when the knowledge base has no such document
     * @throws RetryNotAllowedException unless the document's latest run is {@code FAILED}
     */
    public UUID retry(UUID kbId, UUID documentId) {
        IngestRun run = transactions.execute(status -> {
            documents.lockKnowledgeBase(kbId);
            Optional<IngestRun> latest = runs.latestForDocument(kbId, documentId);
            if (latest.isEmpty()) {
                throw new NotFoundException("Document");
            }
            if (latest.get().status() != RunStatus.FAILED) {
                throw new RetryNotAllowedException(documentId);
            }
            return enqueue(kbId, documentId);
        });
        progress.notify(kbId, RunProgress.status(run, RunStatus.QUEUED));
        return run.runId();
    }

    /** Inserts a {@code QUEUED} ingest run of the document and publishes its event; call inside a transaction. */
    private IngestRun enqueue(UUID kbId, UUID documentId) {
        IngestRun run = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null));
        events.publish(new DomainEvent.IngestRunQueued(run.runId(), kbId, Instant.now()));
        return run;
    }

    /** The override is an explicit lesson id, so it is validated exactly like a frontmatter {@code lesson_id}. */
    private static Map<String, String> withLessonId(Map<String, String> metadata, String lessonId) {
        if (lessonId == null) {
            return metadata;
        }
        Map<String, String> overridden = new HashMap<>(metadata);
        overridden.put(LessonIdentity.KEY_LESSON_ID, lessonId);
        return overridden;
    }

    private record Accepted(UUID documentId, IngestRun run) {}
}
