package dev.mindforge.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "page_supersessions")
public class PageSupersessionEntity implements Persistable<UUID> {

    @Id
    @Column(name = "supersession_id", updatable = false, nullable = false)
    private UUID supersessionId;

    @Transient
    private boolean persisted;

    @Override
    public UUID getId() { return supersessionId; }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "superseded_page_id", nullable = false, updatable = false)
    private UUID supersededPageId;

    @Column(name = "section_anchor", nullable = false, updatable = false, length = 80)
    private String sectionAnchor;

    @Column(name = "superseding_page_id", nullable = false, updatable = false)
    private UUID supersedingPageId;

    @Column(name = "ingest_run_id", nullable = false, updatable = false)
    private UUID ingestRunId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getSupersessionId() { return supersessionId; }
    public void setSupersessionId(UUID supersessionId) { this.supersessionId = supersessionId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public UUID getSupersededPageId() { return supersededPageId; }
    public void setSupersededPageId(UUID supersededPageId) { this.supersededPageId = supersededPageId; }

    public String getSectionAnchor() { return sectionAnchor; }
    public void setSectionAnchor(String sectionAnchor) { this.sectionAnchor = sectionAnchor; }

    public UUID getSupersedingPageId() { return supersedingPageId; }
    public void setSupersedingPageId(UUID supersedingPageId) { this.supersedingPageId = supersedingPageId; }

    public UUID getIngestRunId() { return ingestRunId; }
    public void setIngestRunId(UUID ingestRunId) { this.ingestRunId = ingestRunId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
