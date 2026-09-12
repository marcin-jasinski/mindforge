package dev.mindforge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;

/** Persistence port for {@link Document} aggregates. Every lookup is scoped by knowledge base. */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(UUID knowledgeBaseId, UUID documentId);

    Optional<Document> findByContentHash(UUID knowledgeBaseId, ContentHash contentHash);

    List<Document> listByKnowledgeBase(UUID knowledgeBaseId);
}
