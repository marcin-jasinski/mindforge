package dev.mindforge.infrastructure.ai;

import java.util.Optional;

import com.openai.core.JsonValue;
import com.openai.models.completions.CompletionUsage;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;

/**
 * Writes OpenRouter's {@code usage.cost} onto Spring AI's {@code gen_ai.client.operation} observation as
 * {@code gen_ai.usage.cost}. The observation wraps one provider attempt, so every retry carries its own cost.
 */
public class CostObservationFilter implements ObservationFilter {

    static final String COST_KEY = "gen_ai.usage.cost";

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof ChatModelObservationContext chat) {
            Double cost = costUsd(chat.getResponse());
            if (cost != null) {
                chat.addHighCardinalityKeyValue(KeyValue.of(COST_KEY, cost.toString()));
            }
        }
        return context;
    }

    /** The provider's charge in USD, or {@code null} when the response carries none (not measured, never 0). */
    static Double costUsd(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || !(usage.getNativeUsage() instanceof CompletionUsage nativeUsage)) {
            return null;
        }
        JsonValue cost = nativeUsage._additionalProperties().get("cost");
        Optional<Number> number = cost == null ? Optional.empty() : cost.asNumber();
        return number.map(Number::doubleValue).orElse(null);
    }
}
