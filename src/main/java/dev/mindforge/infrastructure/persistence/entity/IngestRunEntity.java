package dev.mindforge.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** {@code cost} is deliberately unmapped, so no run read through this entity can carry it into a response. */
@Entity
@Table(name = "ingest_runs")
public class IngestRunEntity implements Persistable<UUID> {

    @Id
    @Column(name = "run_id", updatable = false, nullable = false)
    private UUID runId;

    @Transient
    private boolean persisted;

    @Override
    public UUID getId() { return runId; }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 10)
    private RunKind kind;

    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    @Column(name = "reverts_run_id", updatable = false)
    private UUID revertsRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private RunStatus status;

    @Column(name = "attempt", nullable = false, updatable = false)
    private short attempt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retryable")
    private Boolean retryable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failures", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> failures = new ArrayList<>();

    @Column(name = "supersession_skipped", nullable = false)
    private boolean supersessionSkipped;

    @Column(name = "supersession_count", nullable = false)
    private int supersessionCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_versions", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> stepVersions = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> findings = new ArrayList<>();

    /** The database clock, like {@code started_at} and {@code finished_at}, which native updates set with {@code now()}. */
    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at", insertable = false, updatable = false)
    private Instant finishedAt;

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public RunKind getKind() { return kind; }
    public void setKind(RunKind kind) { this.kind = kind; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getRevertsRunId() { return revertsRunId; }
    public void setRevertsRunId(UUID revertsRunId) { this.revertsRunId = revertsRunId; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public short getAttempt() { return attempt; }
    public void setAttempt(short attempt) { this.attempt = attempt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }

    public List<Map<String, Object>> getFailures() { return failures; }
    public void setFailures(List<Map<String, Object>> failures) { this.failures = failures; }

    public boolean isSupersessionSkipped() { return supersessionSkipped; }
    public void setSupersessionSkipped(boolean supersessionSkipped) { this.supersessionSkipped = supersessionSkipped; }

    public int getSupersessionCount() { return supersessionCount; }
    public void setSupersessionCount(int supersessionCount) { this.supersessionCount = supersessionCount; }

    public Map<String, String> getStepVersions() { return stepVersions; }
    public void setStepVersions(Map<String, String> stepVersions) { this.stepVersions = stepVersions; }

    public List<Map<String, Object>> getFindings() { return findings; }
    public void setFindings(List<Map<String, Object>> findings) { this.findings = findings; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
