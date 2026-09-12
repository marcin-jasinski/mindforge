package dev.mindforge.application.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.port.DocumentParser;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.UploadPolicy;

/**
 * Accepts an upload into a knowledge base. The upload is admitted, parsed and given a lesson identity outside
 * any transaction; then one transaction, opened by the knowledge base's row lock so uploads into one knowledge
 * base serialize, returns an identical document, applies the lesson rule, or inserts a new {@link Document}
 * together with its {@link DomainEvent.DocumentIngested}.
 */
public class IngestionService {

    private final UploadPolicy uploadPolicy;
    private final DocumentParser parser;
    private final DocumentRepository documents;
    private final EventPublisher events;
    private final TransactionOperations transactions;

    public IngestionService(UploadPolicy uploadPolicy, DocumentParser parser, DocumentRepository documents,
                            EventPublisher events, TransactionOperations transactions) {
        this.uploadPolicy = uploadPolicy;
        this.parser = parser;
        this.documents = documents;
        this.events = events;
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

        return transactions.execute(status -> {
            documents.lockKnowledgeBase(kbId);
            Optional<Document> identical = documents.findByContentHash(kbId, hash);
            if (identical.isPresent()) {
                return identical.get().documentId();
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
            events.publish(new DomainEvent.DocumentIngested(
                document.documentId(), kbId, lesson.lessonId(), hash, Instant.now()));
            return document.documentId();
        });
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
}
