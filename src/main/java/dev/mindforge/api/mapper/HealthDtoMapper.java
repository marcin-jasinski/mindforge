package dev.mindforge.api.mapper;

import java.util.Optional;

import org.mapstruct.Mapper;

import dev.mindforge.api.dto.response.DanglingSupersessionResponse;
import dev.mindforge.api.dto.response.DuplicateTitleResponse;
import dev.mindforge.api.dto.response.HealthResponse;
import dev.mindforge.api.dto.response.LinkFindingResponse;
import dev.mindforge.api.dto.response.ReviewResponse;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.KnowledgeBaseHealth;

@Mapper(componentModel = "spring")
public interface HealthDtoMapper {

    LinkFindingResponse toResponse(KnowledgeBaseHealth.LinkFinding finding);

    DuplicateTitleResponse toResponse(KnowledgeBaseHealth.DuplicateTitle duplicate);

    DanglingSupersessionResponse toResponse(KnowledgeBaseHealth.DanglingSupersession supersession);

    default HealthResponse toResponse(KnowledgeBaseHealth health, Optional<IngestRun> latestReview) {
        return new HealthResponse(
            health.danglingLinks().stream().map(this::toResponse).toList(),
            health.wrongDirectoryLinks().stream().map(this::toResponse).toList(),
            health.orphanConcepts(),
            health.duplicateConceptTitles().stream().map(this::toResponse).toList(),
            health.danglingSupersessions().stream().map(this::toResponse).toList(),
            health.indexTokens(), health.indexCeilingTokens(), health.prefilterDue(),
            latestReview.map(run -> new ReviewResponse(run.runId(), run.finishedAt(),
                RunDtoMapper.toFindings(run.findings()))).orElse(null));
    }
}
