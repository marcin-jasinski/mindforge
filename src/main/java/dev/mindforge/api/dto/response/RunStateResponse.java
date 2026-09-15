package dev.mindforge.api.dto.response;

import dev.mindforge.domain.model.RunStatus;
import java.util.UUID;

/** Where a document's newest run got to; {@code retryable} is set only on a failed run. */
public record RunStateResponse(
    UUID runId,
    RunStatus status,
    Boolean retryable,
    String failureReason
) {}
