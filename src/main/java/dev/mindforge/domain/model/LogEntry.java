package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A completed run as {@code log.md} tells it. Page counts are derived from the run's revisions: {@code created}
 * counts revision 1, {@code revised} later non-tombstone revisions, {@code deleted} tombstones. The lesson is the
 * run's document's, absent for a revert or a Lint; {@code reverted} is set only for a revert.
 */
public record LogEntry(
    UUID runId,
    RunKind kind,
    Instant finishedAt,
    boolean conversation,
    String lessonId,
    String lessonTitle,
    int created,
    int revised,
    int deleted,
    int supersessionCount,
    RevertedRun reverted
) {

    public record RevertedRun(RunKind kind, Instant finishedAt, boolean conversation, String lessonId,
                              String lessonTitle) {}
}
