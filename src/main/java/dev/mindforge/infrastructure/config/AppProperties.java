package dev.mindforge.infrastructure.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration root for all {@code mindforge.*} application properties.
 * Validated at startup — missing required environment variables (JWT_SECRET, etc.) cause
 * an immediate {@code BindException} before the application accepts traffic.
 */
@ConfigurationProperties(prefix = "mindforge")
@Validated
public class AppProperties {

    @Valid
    private Security security = new Security();

    @Valid
    private Ai ai = new Ai();

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Ai getAi() { return ai; }
    public void setAi(Ai ai) { this.ai = ai; }

    // ---------------------------------------------------------------------------
    // Nested configuration types
    // ---------------------------------------------------------------------------

    public static class Security {

        /** Must be set via JWT_SECRET env var — blank triggers startup failure. */
        @NotBlank
        private String jwtSecret = "";

        @Positive
        private long jwtExpirySeconds = 86400L;

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

        public long getJwtExpirySeconds() { return jwtExpirySeconds; }
        public void setJwtExpirySeconds(long jwtExpirySeconds) { this.jwtExpirySeconds = jwtExpirySeconds; }
    }

    public static class Ai {

        @Valid
        private Model model = new Model();

        @Valid
        private Deadlines deadlines = new Deadlines();

        @Valid
        private Resilience resilience = new Resilience();

        public Model getModel() { return model; }
        public void setModel(Model model) { this.model = model; }

        public Deadlines getDeadlines() { return deadlines; }
        public void setDeadlines(Deadlines deadlines) { this.deadlines = deadlines; }

        public Resilience getResilience() { return resilience; }
        public void setResilience(Resilience resilience) { this.resilience = resilience; }

        /** Model string identifiers routed per {@code ModelTier}. */
        public static class Model {

            @NotBlank
            private String small = "openai/gpt-4o-mini";

            @NotBlank
            private String large = "openai/gpt-4o";

            @NotBlank
            private String vision = "openai/gpt-4o";

            public String getSmall() { return small; }
            public void setSmall(String small) { this.small = small; }

            public String getLarge() { return large; }
            public void setLarge(String large) { this.large = large; }

            public String getVision() { return vision; }
            public void setVision(String vision) { this.vision = vision; }
        }

        /** Per-{@code DeadlineProfile} timeout budget enforced on every gateway call. */
        public static class Deadlines {

            @NotNull
            private Duration interactive = Duration.ofSeconds(10);

            @NotNull
            private Duration batch = Duration.ofSeconds(60);

            @NotNull
            private Duration background = Duration.ofSeconds(300);

            public Duration getInteractive() { return interactive; }
            public void setInteractive(Duration interactive) { this.interactive = interactive; }

            public Duration getBatch() { return batch; }
            public void setBatch(Duration batch) { this.batch = batch; }

            public Duration getBackground() { return background; }
            public void setBackground(Duration background) { this.background = background; }
        }

        /**
         * Resilience4j Retry + CircuitBreaker tuning for the AI gateway. Resilience4j is the
         * single retry authority here — Spring AI's own built-in retry is disabled via
         * {@code spring.ai.retry.max-attempts=1} so these attempts are not multiplied.
         */
        public static class Resilience {

            @Valid
            private RetrySettings retry = new RetrySettings();

            @Valid
            private BreakerSettings circuitBreaker = new BreakerSettings();

            public RetrySettings getRetry() { return retry; }
            public void setRetry(RetrySettings retry) { this.retry = retry; }

            public BreakerSettings getCircuitBreaker() { return circuitBreaker; }
            public void setCircuitBreaker(BreakerSettings circuitBreaker) { this.circuitBreaker = circuitBreaker; }

            /** Exponential-backoff retry over transient provider failures. */
            public static class RetrySettings {

                @Positive
                private int maxAttempts = 3;

                @NotNull
                private Duration initialInterval = Duration.ofMillis(250);

                @Positive
                private double multiplier = 2.0;

                public int getMaxAttempts() { return maxAttempts; }
                public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

                public Duration getInitialInterval() { return initialInterval; }
                public void setInitialInterval(Duration initialInterval) { this.initialInterval = initialInterval; }

                public double getMultiplier() { return multiplier; }
                public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
            }

            /** Count-based circuit breaker shared across all model tiers (single upstream provider). */
            public static class BreakerSettings {

                @Positive
                private float failureRateThreshold = 50f;

                @Positive
                private int slidingWindowSize = 20;

                @Positive
                private int minimumNumberOfCalls = 10;

                @NotNull
                private Duration waitDurationInOpenState = Duration.ofSeconds(30);

                @Positive
                private int permittedCallsInHalfOpenState = 5;

                public float getFailureRateThreshold() { return failureRateThreshold; }
                public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }

                public int getSlidingWindowSize() { return slidingWindowSize; }
                public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }

                public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
                public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }

                public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
                public void setWaitDurationInOpenState(Duration waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }

                public int getPermittedCallsInHalfOpenState() { return permittedCallsInHalfOpenState; }
                public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) { this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState; }
            }
        }
    }
}
