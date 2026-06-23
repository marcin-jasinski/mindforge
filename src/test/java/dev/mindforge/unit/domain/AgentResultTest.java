package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.AgentResult;

class AgentResultTest {

    @Test
    void shouldPatternMatchSuccess() {
        AgentResult result = new AgentResult.Success("summary", 120, 0.0021, 850L);

        assertThat(describe(result)).isEqualTo("ok:summary");
    }

    @Test
    void shouldPatternMatchFailure() {
        AgentResult result = new AgentResult.Failure("timeout", true);

        assertThat(describe(result)).isEqualTo("retryable:timeout");
    }

    // Exercises exhaustive switch over the sealed hierarchy (no default branch).
    private static String describe(AgentResult result) {
        return switch (result) {
            case AgentResult.Success success -> "ok:" + success.outputKey();
            case AgentResult.Failure failure ->
                (failure.retryable() ? "retryable:" : "fatal:") + failure.error();
        };
    }
}
