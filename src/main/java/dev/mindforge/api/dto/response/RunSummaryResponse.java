package dev.mindforge.api.dto.response;

import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import java.time.Instant;
import java.util.UUID;

/** A run in the run list, with page counts derived from its revisions. */
public record RunSummaryResponse(
    UUID runId,
    RunKind kind,
    RunStatus status,
    UUID documentId,
    boolean conversation,
    String lessonTitle,
    int attempt,
    Boolean retryable,
    String failureReason,
    int created,
    int revised,
    int deleted,
    int supersessionCount,
    Instant createdAt,
    Instant finishedAt
) {}
