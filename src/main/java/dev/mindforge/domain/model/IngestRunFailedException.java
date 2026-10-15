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

    /** The reason a run records for {@code e}: a domain exception's own message, never another's internals. */
    public static String reasonFor(RuntimeException e) {
        boolean ours = e.getClass().getPackageName().equals(IngestRunFailedException.class.getPackageName());
        return ours && e.getMessage() != null ? e.getMessage() : "unexpected error";
    }
}
