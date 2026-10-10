package dev.mindforge.api.config;

/** Thrown when a user has spent their allowance of model-calling requests for the current window. */
public class RateLimitExceededException extends RuntimeException {

    private final int limit;
    private final long retryAfterSeconds;

    public RateLimitExceededException(int limit, long retryAfterSeconds) {
        super("Too many requests; retry in " + retryAfterSeconds + "s");
        this.limit = limit;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int limit() { return limit; }

    public long retryAfterSeconds() { return retryAfterSeconds; }
}
