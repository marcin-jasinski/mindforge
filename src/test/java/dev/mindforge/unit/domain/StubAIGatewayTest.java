package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.support.StubAIGateway;

class StubAIGatewayTest {

    @Test
    void shouldReturnConfiguredResponseForTier() {
        StubAIGateway gateway = StubAIGateway.builder()
            .willReturn(ModelTier.LARGE, "canned summary")
            .build();

        CompletionResult result = gateway.complete(ModelTier.LARGE, "summarize this", DeadlineProfile.BATCH);

        assertThat(result.content()).isEqualTo("canned summary");
    }

    @Test
    void shouldFallBackToDefaultResponseWhenTierNotConfigured() {
        StubAIGateway gateway = StubAIGateway.builder().build();

        CompletionResult result = gateway.complete(ModelTier.SMALL, "classify this", DeadlineProfile.INTERACTIVE);

        assertThat(result.content()).isEqualTo("stub response for SMALL");
    }

    @Test
    void shouldCaptureCallsForAssertion() {
        StubAIGateway gateway = StubAIGateway.builder()
            .willReturn(ModelTier.SMALL, "yes")
            .build();

        gateway.complete(ModelTier.SMALL, "is this relevant?", DeadlineProfile.INTERACTIVE);

        assertThat(gateway.recordedCalls()).hasSize(1);
        StubAIGateway.Call call = gateway.recordedCalls().get(0);
        assertThat(call.tier()).isEqualTo(ModelTier.SMALL);
        assertThat(call.prompt()).isEqualTo("is this relevant?");
        assertThat(call.deadline()).isEqualTo(DeadlineProfile.INTERACTIVE);
        assertThat(call.response()).isEqualTo("yes");
    }

    @Test
    void shouldNeverMakeRealHttpCalls() {
        StubAIGateway gateway = StubAIGateway.builder().build();

        assertThat(gateway.embed("some text")).hasSize(1536);
    }
}
