package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.TurnSummary;
import dev.mindforge.infrastructure.persistence.entity.InteractionTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InteractionTurnJpaRepository extends JpaRepository<InteractionTurnEntity, UUID> {

    List<InteractionTurnEntity> findByKnowledgeBaseIdAndInteractionIdOrderByCreatedAt(UUID knowledgeBaseId,
                                                                                    UUID interactionId);

    /** Selects the two redacted columns only, so nothing else of a turn is ever loaded for a listing. */
    @Query("SELECT new dev.mindforge.domain.model.TurnSummary(t.question, t.answer)"
        + " FROM InteractionTurnEntity t, InteractionEntity i"
        + " WHERE t.knowledgeBaseId = :knowledgeBaseId AND i.knowledgeBaseId = :knowledgeBaseId"
        + " AND i.interactionId = t.interactionId AND i.userId = :userId ORDER BY t.createdAt DESC")
    List<TurnSummary> findSummaries(UUID knowledgeBaseId, UUID userId);
}
