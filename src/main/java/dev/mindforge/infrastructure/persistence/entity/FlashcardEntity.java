package dev.mindforge.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.mindforge.domain.model.CardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Inserted only by the native conflict-ignoring insert; JPA reads and updates it. */
@Entity
@Table(name = "flashcards")
@IdClass(FlashcardEntity.Key.class)
public class FlashcardEntity {

    public static class Key implements Serializable {

        private UUID knowledgeBaseId;
        private String cardId;

        public Key() {}

        public Key(UUID knowledgeBaseId, String cardId) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.cardId = cardId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(knowledgeBaseId, key.knowledgeBaseId)
                && Objects.equals(cardId, key.cardId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(knowledgeBaseId, cardId);
        }
    }

    @Id
    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_id", nullable = false, updatable = false, length = 16)
    private String cardId;

    @Column(name = "page_id", nullable = false, updatable = false)
    private UUID pageId;

    @Column(name = "section_anchor", length = 80)
    private String sectionAnchor;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, updatable = false, length = 10)
    private CardType cardType;

    @Column(name = "front", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String front;

    @Column(name = "back", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String back;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_hash", nullable = false, length = 16)
    private String sourceHash;

    @Column(name = "ease_factor", nullable = false)
    private double easeFactor;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "repetitions", nullable = false)
    private int repetitions;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(UUID knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }

    public String getSectionAnchor() { return sectionAnchor; }
    public void setSectionAnchor(String sectionAnchor) { this.sectionAnchor = sectionAnchor; }

    public CardType getCardType() { return cardType; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }

    public String getFront() { return front; }
    public void setFront(String front) { this.front = front; }

    public String getBack() { return back; }
    public void setBack(String back) { this.back = back; }

    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

    public double getEaseFactor() { return easeFactor; }
    public void setEaseFactor(double easeFactor) { this.easeFactor = easeFactor; }

    public int getIntervalDays() { return intervalDays; }
    public void setIntervalDays(int intervalDays) { this.intervalDays = intervalDays; }

    public int getRepetitions() { return repetitions; }
    public void setRepetitions(int repetitions) { this.repetitions = repetitions; }

    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }

    public Instant getRetiredAt() { return retiredAt; }
    public void setRetiredAt(Instant retiredAt) { this.retiredAt = retiredAt; }
}
