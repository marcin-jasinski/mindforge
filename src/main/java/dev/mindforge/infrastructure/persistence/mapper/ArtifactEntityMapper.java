package dev.mindforge.infrastructure.persistence.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.DocumentArtifact;
import dev.mindforge.domain.model.StepCheckpoint;
import dev.mindforge.domain.model.SummaryData;
import dev.mindforge.domain.model.ValidationResult;
import dev.mindforge.infrastructure.persistence.entity.ArtifactEntity;
import dev.mindforge.infrastructure.persistence.entity.StepCheckpointEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", imports = {SummaryData.class, ValidationResult.class})
public interface ArtifactEntityMapper {

    // ---------------------------------------------------------------------------
    // Entity update — used when upserting an existing ArtifactEntity
    // ---------------------------------------------------------------------------

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "artifactId",          ignore = true)
    @Mapping(target = "createdAt",           ignore = true)
    @Mapping(target = "updatedAt",           ignore = true)
    @Mapping(target = "summaryText",
        expression = "java(a.summary() != null ? a.summary().summary() : null)")
    @Mapping(target = "summaryKeyPoints",
        expression = "java(a.summary() != null ? a.summary().keyPoints() : java.util.List.of())")
    @Mapping(target = "relevancePassed",
        expression = "java(a.relevanceValidation() != null ? a.relevanceValidation().passed() : null)")
    @Mapping(target = "relevanceReason",
        expression = "java(a.relevanceValidation() != null ? a.relevanceValidation().reason() : null)")
    @Mapping(target = "relevanceConfidence",
        expression = "java(a.relevanceValidation() != null ? a.relevanceValidation().confidence() : null)")
    void updateEntity(@MappingTarget ArtifactEntity entity, DocumentArtifact a);

    // ---------------------------------------------------------------------------
    // Domain reconstruction — combines entity + checkpoints into the domain record
    // ---------------------------------------------------------------------------

    @Mapping(target = "summary",
        expression = "java(entity.getSummaryText() != null || !entity.getSummaryKeyPoints().isEmpty()" +
            " ? new SummaryData(entity.getSummaryText(), entity.getSummaryKeyPoints()) : null)")
    @Mapping(target = "relevanceValidation",
        expression = "java(entity.getRelevancePassed() != null" +
            " ? new ValidationResult(entity.getRelevancePassed(), entity.getRelevanceReason()," +
            " entity.getRelevanceConfidence() != null ? entity.getRelevanceConfidence() : 0f) : null)")
    @Mapping(target = "stepFingerprints",
        source = "checkpoints", qualifiedByName = "checkpointsToMap")
    DocumentArtifact toDomain(ArtifactEntity entity, List<StepCheckpointEntity> checkpoints);

    @Named("checkpointsToMap")
    default Map<String, StepCheckpoint> checkpointsToMap(List<StepCheckpointEntity> checkpoints) {
        return checkpoints.stream().collect(Collectors.toMap(
            cp -> cp.getId().getOutputKey(),
            cp -> new StepCheckpoint(
                cp.getId().getOutputKey(),
                cp.getFingerprint(),
                cp.getCompletedAt()
            )
        ));
    }
}
