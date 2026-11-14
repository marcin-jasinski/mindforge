package dev.mindforge.application.service;

import java.net.URI;
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
import dev.mindforge.domain.model.DocumentNotRemovableException;
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
 * together with {@link DomainEvent.DocumentAccepted}, its {@code QUEUED} ingest run and
 * {@link DomainEvent.IngestRunQueued}.
 */
public class IngestionService {

    private static final LessonIdentity CONVERSATION_LESSON = new LessonIdentity("conversation", "Conversation");
    private static final String CONVERSATION_FILENAME = "conversation";
    private static final String CONVERSATION_MIME_TYPE = "text/plain";
    private static final String ARTICLE_MIME_TYPE = "text/html";
    /** Length of {@code documents.source_filename}. */
    private static final int MAX_SOURCE_FILENAME_LENGTH = 1000;

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
        return accept(kbId, lesson, hash, filename, upload.mimeType(), parsed.text(), parsed.blocks(),
            upload.source(), upload.uploadedBy(), upload.newVersion()).documentId();
    }

    /**
     * Queues a fetched article exactly as a new upload: an {@code ARTICLE} document under the same lock, dedup,
     * lesson rule and distinct title, but never a new version and never announced by
     * {@link DomainEvent.DocumentAccepted}, so an article is not itself scanned for references. The article is
     * neither admitted nor parsed: HTML is not an upload format, the fetcher has already converted it.
     *
     * @param userId the uploader of the document that referenced the article
     * @param finalUrl the article's URL after redirects; stored as the source filename
     * @param title the article's own title, or null
     * @throws LessonIdentityException when neither the title nor the URL yields a lesson identity
     * @throws LessonAlreadyExistsException when the lesson already has a document
     */
    public ArticleAccepted ingestArticle(UUID kbId, UUID userId, URI finalUrl, String title, String text,
                                         List<ContentBlock> blocks) {
        LessonIdentity lesson = LessonIdentity.resolve(
            title == null ? Map.of() : Map.of(LessonIdentity.KEY_FRONTMATTER_TITLE, title), lastPathSegment(finalUrl));
        ContentHash hash = ContentHash.compute(text.getBytes(StandardCharsets.UTF_8));
        Accepted accepted = accept(kbId, lesson, hash, sourceFilename(finalUrl), ARTICLE_MIME_TYPE, text, blocks,
            UploadSource.ARTICLE, userId, false);
        return new ArticleAccepted(accepted.documentId(), accepted.run() == null);
    }

    /** The document an article became; {@code duplicate} when identical content was already in the knowledge base. */
    public record ArticleAccepted(UUID documentId, boolean duplicate) {}

    /**
     * One transaction, opened by the knowledge base's row lock: returns an identical document (with no run), applies
     * the lesson rule, or inserts the document with its {@code QUEUED} run; notifies the run after commit.
     */
    private Accepted accept(UUID kbId, LessonIdentity lesson, ContentHash hash, String filename, String mimeType,
                            String text, List<ContentBlock> blocks, UploadSource source, UUID uploadedBy,
                            boolean newVersion) {
        Accepted accepted = transactions.execute(status -> {
            documents.lockKnowledgeBase(kbId);
            Optional<Document> identical = documents.findByContentHash(kbId, hash);
            if (identical.isPresent()) {
                return new Accepted(identical.get().documentId(), null);
            }
            Optional<String> existingTitle = documents.findLessonTitle(kbId, lesson.lessonId());
            if (existingTitle.isPresent() && !newVersion) {
                throw new LessonAlreadyExistsException(new LessonIdentity(lesson.lessonId(), existingTitle.get()));
            }
            if (existingTitle.isEmpty() && newVersion) {
                throw new UnknownLessonException(lesson.lessonId());
            }

            // two lessons must not read alike in a citation, so a new lesson whose title is taken carries its id
            LessonIdentity placed = existingTitle.isEmpty()
                && documents.lessonTitleTaken(kbId, lesson.title(), lesson.lessonId())
                ? new LessonIdentity(lesson.lessonId(), distinctTitle(lesson))
                : lesson;
            Document document = documents.insert(kbId, new Document(
                UUID.randomUUID(), kbId, placed, hash, filename, mimeType, text, blocks, source, uploadedBy,
                null, null));
            // an article is not scanned for references, so a fetch never fetches again (D01)
            if (source != UploadSource.ARTICLE) {
                events.publish(new DomainEvent.DocumentAccepted(
                    document.documentId(), kbId, uploadedBy, source, Instant.now()));
            }
            return new Accepted(document.documentId(), enqueue(kbId, document.documentId()));
        });
        if (accepted.run() != null) {
            progress.notify(kbId, RunProgress.status(accepted.run(), RunStatus.QUEUED));
        }
        return accepted;
    }

    /** The last non-empty path segment, else the host: the lesson's fallback when the article has no title. */
    private static String lastPathSegment(URI url) {
        String path = url.getPath();
        if (path != null) {
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isBlank()) {
                    return segments[i];
                }
            }
        }
        return url.getHost();
    }

    /** The URL cut to the column's length, never between the two halves of a surrogate pair. */
    private static String sourceFilename(URI url) {
        String full = url.toString();
        if (full.length() <= MAX_SOURCE_FILENAME_LENGTH) {
            return full;
        }
        int end = Character.isHighSurrogate(full.charAt(MAX_SOURCE_FILENAME_LENGTH - 1))
            ? MAX_SOURCE_FILENAME_LENGTH - 1
            : MAX_SOURCE_FILENAME_LENGTH;
        return full.substring(0, end);
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

    /** "Mitosis" becomes "Mitosis (mitosis-2)", kept inside the title length the lesson identity allows. */
    private static String distinctTitle(LessonIdentity lesson) {
        String suffix = " (" + lesson.lessonId() + ")";
        String title = lesson.title();
        int room = LessonIdentity.MAX_TITLE_LENGTH - suffix.length();
        return (title.length() > room ? title.substring(0, Math.max(room, 0)).stripTrailing() : title) + suffix;
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
     * Removes a document whose every run failed, so a file the pipeline refused can be uploaded again; an identical
     * upload is otherwise answered with the failed document's id. Pages, revisions and sources are untouched: a
     * failed run wrote none.
     *
     * @throws NotFoundException when the knowledge base has no such document
     * @throws DocumentNotRemovableException when any run of the document is queued, running, written or completed
     */
    public void remove(UUID kbId, UUID documentId) {
        transactions.executeWithoutResult(status -> {
            documents.lockKnowledgeBase(kbId);
            if (documents.findById(kbId, documentId).isEmpty()) {
                throw new NotFoundException("Document");
            }
            if (!runs.onlyFailedRuns(kbId, documentId)) {
                throw new DocumentNotRemovableException(documentId);
            }
            runs.deleteForDocument(kbId, documentId);
            documents.delete(kbId, documentId);
        });
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
