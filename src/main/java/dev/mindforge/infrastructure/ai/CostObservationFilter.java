package dev.mindforge.infrastructure.ai;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
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
            Double cost = AIGatewayAdapter.costUsd(chat.getResponse());
            if (cost != null) {
                chat.addHighCardinalityKeyValue(KeyValue.of(COST_KEY, cost.toString()));
            }
        }
        return context;
    }
}
