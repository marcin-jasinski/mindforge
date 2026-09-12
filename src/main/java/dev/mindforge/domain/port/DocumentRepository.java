package dev.mindforge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;

/** Persistence port for {@link Document}s. Every method takes the knowledge base first. */
public interface DocumentRepository {

    /** Locks the knowledge base's row until the caller's transaction ends, serializing uploads into it. */
    void lockKnowledgeBase(UUID knowledgeBaseId);

    /** Inserts a new document; never overwrites an existing one. */
    Document insert(UUID knowledgeBaseId, Document document);

    Optional<Document> findById(UUID knowledgeBaseId, UUID documentId);

    Optional<Document> findByContentHash(UUID knowledgeBaseId, ContentHash contentHash);

    /** The title of the lesson's most recent document; empty when the lesson has no document here. */
    Optional<String> findLessonTitle(UUID knowledgeBaseId, String lessonId);

    List<Document> listByKnowledgeBase(UUID knowledgeBaseId);
}
