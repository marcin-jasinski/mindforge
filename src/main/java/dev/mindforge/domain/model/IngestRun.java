package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One execution that changes the wiki, and the record of what it changed. Pages written are derived from its
 * revisions; the run stores only what rows cannot tell. {@code failures} are what its steps dropped and
 * {@code findings} what a Lint reported.
 */
public record IngestRun(
    UUID runId,
    UUID knowledgeBaseId,
    RunKind kind,
    UUID documentId,
    UUID revertsRunId,
    RunStatus status,
    int attempt,
    String failureReason,
    Boolean retryable,
    List<RunFailure> failures,
    boolean supersessionSkipped,
    int supersessionCount,
    Map<String, String> stepVersions,
    List<ReviewItem> findings,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt
) {

    public IngestRun {
        failures = failures == null ? List.of() : List.copyOf(failures);
        stepVersions = stepVersions == null ? Map.of() : Map.copyOf(stepVersions);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** A new first-attempt run waiting for the lease. */
    public static IngestRun queued(UUID knowledgeBaseId, RunKind kind, UUID documentId, UUID revertsRunId) {
        return new IngestRun(UUID.randomUUID(), knowledgeBaseId, kind, documentId, revertsRunId, RunStatus.QUEUED,
            1, null, null, List.of(), false, 0, Map.of(), List.of(), null, null, null);
    }
}
