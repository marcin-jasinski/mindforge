package dev.mindforge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DocumentStatus;

/** Persistence port for {@link Document} aggregates. */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(UUID documentId);

    Optional<Document> findByContentHash(ContentHash contentHash);

    void updateStatus(UUID documentId, DocumentStatus status);

    List<Document> listByKnowledgeBase(UUID knowledgeBaseId);
}
