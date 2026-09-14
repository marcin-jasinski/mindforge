package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A best-effort progress message for one run. {@code step} is null on a status change; {@code done} and
 * {@code total} are set only while pages are written; {@code documentId} is null for a Lint or a revert.
 */
public record RunProgress(UUID runId, RunKind kind, UUID documentId, RunStatus status, String step, Integer done,
                          Integer total, Instant at) {

    public static RunProgress status(IngestRun run, RunStatus status) {
        return new RunProgress(run.runId(), run.kind(), run.documentId(), status, null, null, null, Instant.now());
    }

    public static RunProgress step(IngestRun run, String step, Integer done, Integer total) {
        return new RunProgress(run.runId(), run.kind(), run.documentId(), RunStatus.RUNNING, step, done, total,
            Instant.now());
    }
}
