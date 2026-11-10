package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.infrastructure.ai.CostObservationFilter;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;

class CostObservationFilterTest {

    @Test
    void shouldWriteProviderCostOntoTheChatObservation() {
        ChatModelObservationContext context = makeContext();
        context.setResponse(AIGatewayAdapterTest.fixtureResponseWithCost(0.0042));

        Observation.Context filtered = new CostObservationFilter().map(context);

        assertThat(filtered.getHighCardinalityKeyValue("gen_ai.usage.cost"))
            .isEqualTo(KeyValue.of("gen_ai.usage.cost", "0.0042"));
    }

    @Test
    void shouldLeaveCostOffWhenProviderSentNone() {
        ChatModelObservationContext context = makeContext();

        Observation.Context filtered = new CostObservationFilter().map(context);

        assertThat(filtered.getHighCardinalityKeyValue("gen_ai.usage.cost")).isNull();
    }

    private static ChatModelObservationContext makeContext() {
        return ChatModelObservationContext.builder().prompt(new Prompt("hello")).provider("openai").build();
    }
}
