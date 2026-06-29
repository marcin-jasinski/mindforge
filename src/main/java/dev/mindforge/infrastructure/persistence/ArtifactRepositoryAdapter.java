package dev.mindforge.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.ConceptMapData;
import dev.mindforge.domain.model.DocumentArtifact;
import dev.mindforge.domain.model.SummaryData;
import dev.mindforge.domain.model.StepCheckpoint;
import dev.mindforge.domain.model.ValidationResult;
import dev.mindforge.domain.port.ArtifactRepository;
import dev.mindforge.infrastructure.persistence.entity.ArtifactEntity;
import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity;
import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity.StepCheckpointId;
import org.springframework.transaction.annotation.Transactional;
/** Adapts JPA repositories to the domain {@link ArtifactRepository} port. */
public class ArtifactRepositoryAdapter implements ArtifactRepository {

    private final ArtifactJpaRepository artifactJpaRepository;
    private final StepCheckpointJpaRepository checkpointJpaRepository;

    public ArtifactRepositoryAdapter(
        ArtifactJpaRepository artifactJpaRepository,
        StepCheckpointJpaRepository checkpointJpaRepository) {
        this.artifactJpaRepository = artifactJpaRepository;
        this.checkpointJpaRepository = checkpointJpaRepository;
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
        populateEntity(entity, artifact);
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
            return toDomain(entity, checkpoints);
        });
    }

    @Override
    public long countFlashcards(UUID knowledgeBaseId) {
        Long count = artifactJpaRepository.countFlashcardsByKnowledgeBaseId(knowledgeBaseId);
        return count != null ? count : 0L;
    }

    // ---------------------------------------------------------------------------
    // Mapping helpers
    // ---------------------------------------------------------------------------

    private void populateEntity(ArtifactEntity e, DocumentArtifact a) {
        e.setDocumentId(a.documentId());
        e.setKnowledgeBaseId(a.knowledgeBaseId());

        if (a.summary() != null) {
            e.setSummaryText(a.summary().summary());
            e.setSummaryKeyPoints(a.summary().keyPoints());
        }

        e.setFlashcards(a.flashcards());
        e.setConceptMap(a.conceptMap());
        e.setQuizQuestions(a.quizQuestions());
        e.setCompletedStep(a.completedStep());

        if (a.relevanceValidation() != null) {
            e.setRelevancePassed(a.relevanceValidation().passed());
            e.setRelevanceReason(a.relevanceValidation().reason());
            e.setRelevanceConfidence(a.relevanceValidation().confidence());
        }
    }

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

    private DocumentArtifact toDomain(ArtifactEntity e, List<StepCheckpointEntity> checkpoints) {
        SummaryData summary = (e.getSummaryText() != null || !e.getSummaryKeyPoints().isEmpty())
            ? new SummaryData(e.getSummaryText(), e.getSummaryKeyPoints())
            : null;

        ValidationResult relevance = e.getRelevancePassed() != null
            ? new ValidationResult(
                e.getRelevancePassed(),
                e.getRelevanceReason(),
                e.getRelevanceConfidence() != null ? e.getRelevanceConfidence() : 0f)
            : null;

        ConceptMapData conceptMap = e.getConceptMap();

        Map<String, StepCheckpoint> fingerprints = checkpoints.stream()
            .collect(Collectors.toMap(
                cp -> cp.getId().getOutputKey(),
                cp -> new StepCheckpoint(
                    cp.getId().getOutputKey(),
                    cp.getFingerprint(),
                    cp.getCompletedAt()
                )
            ));

        return new DocumentArtifact(
            e.getArtifactId(),
            e.getDocumentId(),
            e.getKnowledgeBaseId(),
            summary,
            e.getFlashcards(),
            conceptMap,
            e.getQuizQuestions(),
            relevance,
            fingerprints,
            e.getCompletedStep(),
            e.getCreatedAt()
        );
    }
}
