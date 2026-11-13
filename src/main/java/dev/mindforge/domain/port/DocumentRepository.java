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

    /** The whole document, its content blocks included (with an IMAGE block's bytes). */
    Optional<Document> findById(UUID knowledgeBaseId, UUID documentId);

    /**
     * The non-conversation document with this content hash, for the upload dedup check. Its {@code contentBlocks} is
     * empty: the blocks, which can hold image bytes, are not loaded.
     */
    Optional<Document> findByContentHash(UUID knowledgeBaseId, ContentHash contentHash);

    /** The title of the lesson's most recent document; empty when the lesson has no document here. */
    Optional<String> findLessonTitle(UUID knowledgeBaseId, String lessonId);

    /**
     * Every document of the knowledge base. Each one's {@code contentBlocks} is empty: the blocks, which can hold
     * image bytes, are not loaded; use {@link #findById} for them.
     */
    List<Document> listByKnowledgeBase(UUID knowledgeBaseId);

    /** True when a lesson other than {@code lessonId} already carries this title, ignoring case. */
    boolean lessonTitleTaken(UUID knowledgeBaseId, String title, String lessonId);

    /** Removes the document's row; its runs must already be gone. */
    void delete(UUID knowledgeBaseId, UUID documentId);
}
