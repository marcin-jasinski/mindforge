package dev.mindforge.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.QuizSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuizSessionJpaRepository extends JpaRepository<QuizSessionEntity, UUID> {

    Optional<QuizSessionEntity> findByKnowledgeBaseIdAndSessionIdAndExpiresAtAfter(UUID knowledgeBaseId,
                                                                                  UUID sessionId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE QuizSessionEntity q SET q.cursor = :cursor"
        + " WHERE q.knowledgeBaseId = :knowledgeBaseId AND q.sessionId = :sessionId")
    int updateCursor(UUID knowledgeBaseId, UUID sessionId, int cursor);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM QuizSessionEntity q WHERE q.expiresAt <= :now")
    int deleteExpired(Instant now);
}
