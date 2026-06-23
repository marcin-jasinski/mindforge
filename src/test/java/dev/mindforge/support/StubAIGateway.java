package dev.mindforge.support;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;

public final class StubAIGateway implements AIGateway {

    private final Map<ModelTier, String> responses;
    private final List<Call> recordedCalls = new ArrayList<>();

    private StubAIGateway(Map<ModelTier, String> responses) {
        this.responses = responses;
    }

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline) {
        String content = responses.getOrDefault(tier, "stub response for " + tier);
        recordedCalls.add(new Call(tier, prompt, deadline, content));
        return new CompletionResult(content, 10, 20, "stub-model", "stub", 0L, 0.0);
    }

    @Override
    public float[] embed(String text) {
        return new float[1536];
    }

    public List<Call> recordedCalls() {
        return List.copyOf(recordedCalls);
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Call(ModelTier tier, String prompt, DeadlineProfile deadline, String response) {}

    public static final class Builder {

        private final Map<ModelTier, String> responses = new EnumMap<>(ModelTier.class);

        public Builder willReturn(ModelTier tier, String response) {
            responses.put(tier, response);
            return this;
        }

        public StubAIGateway build() {
            return new StubAIGateway(Map.copyOf(responses));
        }
    }
}
