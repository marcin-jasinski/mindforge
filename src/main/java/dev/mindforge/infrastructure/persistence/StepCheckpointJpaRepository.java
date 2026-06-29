package dev.mindforge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity;
import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity.StepCheckpointId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepCheckpointJpaRepository
    extends JpaRepository<StepCheckpointEntity, StepCheckpointId> {

    List<StepCheckpointEntity> findByIdDocumentId(UUID documentId);

    void deleteByIdDocumentId(UUID documentId);
}
