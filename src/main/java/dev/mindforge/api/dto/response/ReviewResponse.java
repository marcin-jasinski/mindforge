package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The newest completed full Lint's findings and suggestions. */
public record ReviewResponse(
    UUID runId,
    Instant finishedAt,
    List<FindingResponse> findings
) {}
