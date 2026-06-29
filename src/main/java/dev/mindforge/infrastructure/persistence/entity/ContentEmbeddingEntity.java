package dev.mindforge.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores text chunks and their semantic embedding vectors for pgvector similarity search.
 * The {@code embedding} column uses PostgreSQL's {@code vector(1536)} type provided by the
 * pgvector extension. Full JPA mapping for the vector column is wired in Phase 11.
 */
@Entity
@Table(name = "content_embeddings")
public class ContentEmbeddingEntity extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private UUID knowledgeBaseId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
