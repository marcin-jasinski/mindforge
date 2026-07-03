package dev.mindforge.infrastructure.config;

import java.io.IOException;
import java.time.Duration;

import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.AIGatewayAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@link AIGateway} adapter bean against the Spring AI OpenAI client. */
@Configuration
public class AiConfig {

    @Bean
    AIGateway aiGateway(ChatModel chatModel,
                        EmbeddingModel embeddingModel,
                        AppProperties properties,
                        Retry aiGatewayRetry,
                        CircuitBreaker aiGatewayCircuitBreaker) {
        return new AIGatewayAdapter(chatModel, embeddingModel, properties, aiGatewayRetry, aiGatewayCircuitBreaker);
    }

    /**
     * Retry over transient provider failures only. {@code TransientAiException} (5xx,
     * rate limits) and raw {@code IOException} (connection resets/timeouts) are retried;
     * everything else — notably {@code NonTransientAiException} (bad request, auth, unknown
     * model) and an open-circuit rejection — is left to propagate on the first attempt.
     */
    @Bean
    Retry aiGatewayRetry(AppProperties properties) {
        AppProperties.Ai.Resilience.RetrySettings settings = properties.getAi().getResilience().getRetry();
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(settings.getMaxAttempts())
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                settings.getInitialInterval(), settings.getMultiplier()))
            .retryExceptions(TransientAiException.class, IOException.class)
            .build();
        return Retry.of("ai-gateway", config);
    }

    /**
     * Single count-based circuit breaker shared across all model tiers — failures against
     * OpenRouter are provider-wide, not model-specific. Transient failures and deadline
     * timeouts count toward tripping it open; {@code NonTransientAiException} (a client-side
     * bad request) does not, so a malformed prompt cannot open the circuit for everyone.
     */
    @Bean
    CircuitBreaker aiGatewayCircuitBreaker(AppProperties properties) {
        AppProperties.Ai.Resilience.BreakerSettings settings = properties.getAi().getResilience().getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(settings.getSlidingWindowSize())
            .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
            .failureRateThreshold(settings.getFailureRateThreshold())
            .waitDurationInOpenState(settings.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(settings.getPermittedCallsInHalfOpenState())
            .recordExceptions(TransientAiException.class, IOException.class, DeadlineExceededException.class)
            .build();
        return CircuitBreaker.of("ai-gateway", config);
    }
}
