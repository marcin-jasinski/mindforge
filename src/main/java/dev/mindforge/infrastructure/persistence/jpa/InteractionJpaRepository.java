package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.InteractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionJpaRepository extends JpaRepository<InteractionEntity, UUID> {

    Optional<InteractionEntity> findByKnowledgeBaseIdAndInteractionId(UUID knowledgeBaseId, UUID interactionId);
}
