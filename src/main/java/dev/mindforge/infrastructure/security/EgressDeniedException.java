package dev.mindforge.infrastructure.security;

/**
 * Thrown when {@link EgressPolicy} refuses an outbound URL or host. The message is fixed per
 * {@link Reason} and never contains any part of the URL.
 */
public class EgressDeniedException extends SecurityException {

    /** Why the URL or host was refused. */
    public enum Reason {
        SCHEME("The URL scheme is not allowed"),
        PORT("The URL port is not allowed"),
        USERINFO("The URL must not contain user information"),
        HOST("The URL has no valid host"),
        ADDRESS("The host address is not allowed"),
        UNRESOLVABLE("The host could not be resolved");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public EgressDeniedException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
