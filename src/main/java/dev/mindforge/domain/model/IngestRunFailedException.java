package dev.mindforge.domain.model;

/** Ends a run as {@code FAILED} with this reason; {@code retryable} says whether trying again can help. */
public class IngestRunFailedException extends IllegalStateException {

    private final boolean retryable;

    public IngestRunFailedException(String reason, boolean retryable) {
        super(reason);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
