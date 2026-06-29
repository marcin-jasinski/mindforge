package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseJpaRepository extends JpaRepository<KnowledgeBaseEntity, UUID> {

    List<KnowledgeBaseEntity> findByOwnerId(UUID ownerId);
}
