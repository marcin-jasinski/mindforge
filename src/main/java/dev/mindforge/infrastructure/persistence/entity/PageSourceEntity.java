package dev.mindforge.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/** One document's contribution to one page, per run. */
@Entity
@Table(name = "page_sources")
@IdClass(PageSourceEntity.Key.class)
public class PageSourceEntity implements Persistable<PageSourceEntity.Key> {

    public record Key(UUID pageId, UUID ingestRunId) implements Serializable {}

    @Id
    @Column(name = "page_id", updatable = false, nullable = false)
    private UUID pageId;

    @Id
    @Column(name = "ingest_run_id", updatable = false, nullable = false)
    private UUID ingestRunId;

    @Transient
    private boolean persisted;

    @Override
    public Key getId() { return new Key(pageId, ingestRunId); }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }

    public UUID getIngestRunId() { return ingestRunId; }
    public void setIngestRunId(UUID ingestRunId) { this.ingestRunId = ingestRunId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
}
