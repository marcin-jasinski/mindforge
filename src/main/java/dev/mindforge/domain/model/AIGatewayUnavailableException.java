package dev.mindforge.domain.model;

/**
 * Thrown when the {@code AIGateway} cannot get an answer from the provider: its circuit breaker is
 * open and rejects the call outright, or the provider call itself failed (unreachable, refused, an
 * HTTP error). Distinct from {@link DeadlineExceededException}: the deadline case means a call ran
 * but outlived its timeout budget. The provider's own message stays on the cause and in the log;
 * only fixed text reaches a run's failure reason or an API response.
 */
public class AIGatewayUnavailableException extends RuntimeException {

    public AIGatewayUnavailableException(Throwable cause) {
        super("AI gateway circuit is open — provider unavailable", cause);
    }

    public AIGatewayUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
