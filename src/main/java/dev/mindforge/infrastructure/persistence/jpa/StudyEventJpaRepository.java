package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.StudyEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudyEventJpaRepository extends JpaRepository<StudyEventEntity, Long> {

    interface PageScoreRow {
        UUID getPageId();
        Double getMean();
    }

    /** Each page's mean over its last five events, card ratings and quiz scores alike (T27). */
    @Query(value = "SELECT page_id AS pageId, CAST(AVG(score) AS DOUBLE PRECISION) AS mean FROM ("
        + " SELECT page_id, score, ROW_NUMBER() OVER (PARTITION BY page_id ORDER BY occurred_at DESC, event_id DESC) AS n"
        + " FROM study_events WHERE knowledge_base_id = :knowledgeBaseId) recent"
        + " WHERE n <= 5 GROUP BY page_id", nativeQuery = true)
    List<PageScoreRow> findPageScores(UUID knowledgeBaseId);
}
