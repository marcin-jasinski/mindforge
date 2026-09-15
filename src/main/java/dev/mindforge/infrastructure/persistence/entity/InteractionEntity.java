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
@Table(name = "interactions")
public class InteractionEntity implements Persistable<UUID> {

    @Id
    @Column(name = "interaction_id", updatable = false, nullable = false)
    private UUID interactionId;

    @Transient
    private boolean persisted;

    @Override
    public UUID getId() { return interactionId; }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    public UUID getInteractionId() { return interactionId; }
    public void setInteractionId(UUID interactionId) { this.interactionId = interactionId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
}
