package dev.mindforge.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One card rating or graded quiz answer; appended, never updated. */
@Entity
@Table(name = "study_events")
public class StudyEventEntity {

    public static final String CARD = "CARD";
    public static final String QUIZ = "QUIZ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", updatable = false, nullable = false)
    private Long eventId;

    @Column(name = "knowledge_base_id", nullable = false, updatable = false)
    private UUID knowledgeBaseId;

    @Column(name = "page_id", nullable = false, updatable = false)
    private UUID pageId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_id", updatable = false, length = 16)
    private String cardId;

    @Column(name = "kind", nullable = false, updatable = false, length = 4)
    private String kind;

    @Column(name = "score", nullable = false, updatable = false)
    private short score;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static StudyEventEntity of(UUID knowledgeBaseId, UUID pageId, String cardId, String kind, int score,
                                      Instant occurredAt) {
        StudyEventEntity event = new StudyEventEntity();
        event.knowledgeBaseId = knowledgeBaseId;
        event.pageId = pageId;
        event.cardId = cardId;
        event.kind = kind;
        event.score = (short) score;
        event.occurredAt = occurredAt;
        return event;
    }

    public Long getEventId() { return eventId; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public UUID getPageId() { return pageId; }
    public String getCardId() { return cardId; }
    public String getKind() { return kind; }
    public short getScore() { return score; }
    public Instant getOccurredAt() { return occurredAt; }
}
