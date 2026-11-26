package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when a stop is asked for a run that is not a queued or running ingest, or that has begun writing pages. */
public class RunNotCancellableException extends IllegalStateException {

    public RunNotCancellableException(UUID runId) {
        super("Run " + runId + " cannot be stopped: only a queued or running ingest can be, before it writes pages");
    }
}
