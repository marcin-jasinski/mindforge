package dev.mindforge.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
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

/** An append-only revision; never updated or deleted. */
@Entity
@Table(name = "page_revisions")
@IdClass(PageRevisionEntity.Key.class)
public class PageRevisionEntity implements Persistable<PageRevisionEntity.Key> {

    public record Key(UUID pageId, int revision) implements Serializable {}

    @Id
    @Column(name = "page_id", updatable = false, nullable = false)
    private UUID pageId;

    @Id
    @Column(name = "revision", updatable = false, nullable = false)
    private int revision;

    @Transient
    private boolean persisted;

    @Override
    public Key getId() { return new Key(pageId, revision); }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "ingest_run_id", nullable = false, updatable = false)
    private UUID ingestRunId;

    @Column(name = "path", nullable = false, updatable = false, length = 89)
    private String path;

    @Column(name = "title", nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, updatable = false, length = 300)
    private String description;

    @Column(name = "page_type", nullable = false, updatable = false, length = 50)
    private String pageType;

    @Column(name = "markdown_body", updatable = false, columnDefinition = "TEXT")
    private String markdownBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }

    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public UUID getIngestRunId() { return ingestRunId; }
    public void setIngestRunId(UUID ingestRunId) { this.ingestRunId = ingestRunId; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }

    public String getMarkdownBody() { return markdownBody; }
    public void setMarkdownBody(String markdownBody) { this.markdownBody = markdownBody; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
