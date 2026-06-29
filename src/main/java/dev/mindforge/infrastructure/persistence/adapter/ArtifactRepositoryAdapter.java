package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.DocumentArtifact;
import dev.mindforge.domain.model.StepCheckpoint;
import dev.mindforge.domain.port.ArtifactRepository;
import dev.mindforge.infrastructure.persistence.entity.ArtifactEntity;
import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity;
import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity.StepCheckpointId;
import dev.mindforge.infrastructure.persistence.jpa.ArtifactJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.StepCheckpointJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.ArtifactEntityMapper;
import org.springframework.transaction.annotation.Transactional;

public class ArtifactRepositoryAdapter implements ArtifactRepository {

    private final ArtifactJpaRepository artifactJpaRepository;
    private final StepCheckpointJpaRepository checkpointJpaRepository;
    private final ArtifactEntityMapper mapper;

    public ArtifactRepositoryAdapter(
        ArtifactJpaRepository artifactJpaRepository,
        StepCheckpointJpaRepository checkpointJpaRepository,
        ArtifactEntityMapper mapper) {
        this.artifactJpaRepository = artifactJpaRepository;
        this.checkpointJpaRepository = checkpointJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public DocumentArtifact saveCheckpoint(DocumentArtifact artifact) {
        ArtifactEntity entity = artifactJpaRepository
            .findByDocumentId(artifact.documentId())
            .orElseGet(ArtifactEntity::new);

        if (entity.getArtifactId() == null) {
            entity.setArtifactId(artifact.artifactId());
        }
        mapper.updateEntity(entity, artifact);
        artifactJpaRepository.save(entity);

        replaceCheckpoints(artifact);

        return loadLatest(artifact.documentId())
            .orElseThrow(() -> new IllegalStateException(
                "Artifact not found after save for document " + artifact.documentId()));
    }

    @Override
    public Optional<DocumentArtifact> loadLatest(UUID documentId) {
        return artifactJpaRepository.findByDocumentId(documentId).map(entity -> {
            List<StepCheckpointEntity> checkpoints =
                checkpointJpaRepository.findByIdDocumentId(documentId);
            return mapper.toDomain(entity, checkpoints);
        });
    }

    @Override
    public long countFlashcards(UUID knowledgeBaseId) {
        Long count = artifactJpaRepository.countFlashcardsByKnowledgeBaseId(knowledgeBaseId);
        return count != null ? count : 0L;
    }

    // ---------------------------------------------------------------------------
    // Checkpoint persistence — separate table, managed explicitly
    // ---------------------------------------------------------------------------

    private void replaceCheckpoints(DocumentArtifact artifact) {
        checkpointJpaRepository.deleteByIdDocumentId(artifact.documentId());
        checkpointJpaRepository.flush();

        List<StepCheckpointEntity> entities = artifact.stepFingerprints().entrySet().stream()
            .map(entry -> {
                StepCheckpoint cp = entry.getValue();
                StepCheckpointEntity cpe = new StepCheckpointEntity();
                cpe.setId(new StepCheckpointId(artifact.documentId(), cp.outputKey()));
                cpe.setFingerprint(cp.fingerprint());
                cpe.setCompletedAt(cp.completedAt());
                return cpe;
            })
            .toList();

        if (!entities.isEmpty()) {
            checkpointJpaRepository.saveAll(entities);
        }
    }
}
