package dev.mindforge.domain.model;

/**
 * Thrown when the {@code AIGateway}'s circuit breaker is open and rejects a call outright,
 * so no request reaches the provider. Distinct from {@link DeadlineExceededException}: the
 * deadline case means a call ran but outlived its timeout budget; this case means the gateway
 * gave up early because the provider is unhealthy.
 */
public class AIGatewayUnavailableException extends RuntimeException {

    public AIGatewayUnavailableException(Throwable cause) {
        super("AI gateway circuit is open — provider unavailable", cause);
    }
}
