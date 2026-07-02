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

        public Model getModel() { return model; }
        public void setModel(Model model) { this.model = model; }

        public Deadlines getDeadlines() { return deadlines; }
        public void setDeadlines(Deadlines deadlines) { this.deadlines = deadlines; }

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
    }
}
