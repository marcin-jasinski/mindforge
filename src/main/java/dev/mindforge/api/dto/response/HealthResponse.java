package dev.mindforge.api.dto.response;

import java.util.List;

/** The live health of a knowledge base and its latest full review, null before one completed. */
public record HealthResponse(
    List<LinkFindingResponse> danglingLinks,
    List<LinkFindingResponse> wrongDirectoryLinks,
    List<String> orphanConcepts,
    List<DuplicateTitleResponse> duplicateConceptTitles,
    List<DanglingSupersessionResponse> danglingSupersessions,
    int indexTokens,
    int indexCeilingTokens,
    boolean prefilterDue,
    ReviewResponse latestReview
) {}
