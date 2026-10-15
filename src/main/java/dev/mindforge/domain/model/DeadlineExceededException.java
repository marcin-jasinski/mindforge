package dev.mindforge.domain.model;

import java.time.Duration;

/**
 * Thrown when an {@code AIGateway} call does not complete within the timeout budget
 * of its {@link DeadlineProfile}.
 */
public class DeadlineExceededException extends RuntimeException {

    public DeadlineExceededException(DeadlineProfile deadline, Duration timeout) {
        super("AI gateway call exceeded " + deadline + " deadline of " + timeout.toSeconds() + "s");
    }
}
