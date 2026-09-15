package dev.mindforge.support;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;

/**
 * Deterministic {@link AIGateway}: the first answer whose fragment the prompt contains wins, then the tier's canned
 * response, then a placeholder. Thread-safe, since a run writes pages in parallel.
 */
public final class StubAIGateway implements AIGateway {

    private final Map<ModelTier, String> responses;
    private final List<Answer> answers = new CopyOnWriteArrayList<>();
    private final List<Call> recordedCalls = new CopyOnWriteArrayList<>();

    public StubAIGateway() {
        this(Map.of());
    }

    private StubAIGateway(Map<ModelTier, String> responses) {
        this.responses = responses;
    }

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline) {
        String content = answers.stream()
            .filter(answer -> prompt.contains(answer.promptFragment()))
            .findFirst()
            .map(answer -> answer.responder().apply(prompt))
            .orElseGet(() -> responses.getOrDefault(tier, "stub response for " + tier));
        recordedCalls.add(new Call(tier, prompt, deadline, content));
        return new CompletionResult(content, 10, 20, "stub-model", "stub", 0L, 0.0);
    }

    /** Answers every prompt containing the fragment, unless an earlier answer matches first. */
    public StubAIGateway answer(String promptFragment, String response) {
        return answer(promptFragment, prompt -> response);
    }

    /** Answers with whatever the responder returns or throws for the prompt. */
    public StubAIGateway answer(String promptFragment, UnaryOperator<String> responder) {
        answers.add(new Answer(promptFragment, responder));
        return this;
    }

    public void reset() {
        answers.clear();
        recordedCalls.clear();
    }

    public List<Call> recordedCalls() {
        return List.copyOf(recordedCalls);
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Call(ModelTier tier, String prompt, DeadlineProfile deadline, String response) {}

    private record Answer(String promptFragment, UnaryOperator<String> responder) {}

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
