package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A run as the run list shows it. Page counts are derived from its revisions, as {@link LogEntry}'s are; the lesson
 * is the run's document's, absent for a revert or a Lint.
 */
public record RunSummary(
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
