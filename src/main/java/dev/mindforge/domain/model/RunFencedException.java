package dev.mindforge.domain.model;

import java.util.UUID;

/**
 * Thrown when a run's status update matches no row: the run is not in the expected status or no longer holds its
 * knowledge base's lease. The transaction rolls back and the caller writes, releases and notifies nothing.
 */
public class RunFencedException extends IllegalStateException {

    public RunFencedException(UUID runId, RunStatus expected) {
        super("Run " + runId + " is no longer " + expected + " under the lease");
    }
}
