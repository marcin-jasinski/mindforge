package dev.mindforge.domain.model;

/** What an {@link IngestRun} does to the wiki. A conversation edit is an {@code INGEST} of a conversation turn. */
public enum RunKind {
    INGEST,
    REVERT,
    LINT
}
