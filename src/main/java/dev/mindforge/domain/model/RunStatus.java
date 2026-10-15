package dev.mindforge.domain.model;

/**
 * Where an {@link IngestRun} got to: {@code QUEUED → RUNNING → WRITTEN → COMPLETED}, or {@code FAILED} from
 * {@code RUNNING}. {@code WRITTEN} means pages are committed and supersessions are not.
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    WRITTEN,
    COMPLETED,
    FAILED
}
