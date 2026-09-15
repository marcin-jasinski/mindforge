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
@Table(name = "interaction_turns")
public class InteractionTurnEntity implements Persistable<UUID> {

    @Id
    @Column(name = "turn_id", updatable = false, nullable = false)
    private UUID turnId;

    @Transient
    private boolean persisted;

    @Override
    public UUID getId() { return turnId; }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "interaction_id", nullable = false, updatable = false)
    private UUID interactionId;

    @Column(name = "question", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "used_page_paths", nullable = false, updatable = false, columnDefinition = "TEXT[]")
    private String[] usedPagePaths = new String[0];

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID turnId) { this.turnId = turnId; }

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public UUID getInteractionId() { return interactionId; }
    public void setInteractionId(UUID interactionId) { this.interactionId = interactionId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String[] getUsedPagePaths() { return usedPagePaths; }
    public void setUsedPagePaths(String[] usedPagePaths) { this.usedPagePaths = usedPagePaths; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
