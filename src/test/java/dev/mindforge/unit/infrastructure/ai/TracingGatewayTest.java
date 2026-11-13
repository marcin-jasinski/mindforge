package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.RunCost;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.infrastructure.ai.ObservationRunMeter;
import dev.mindforge.infrastructure.ai.TracingGateway;
import dev.mindforge.support.StubAIGateway;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

/** An image call gets the model service's span and adds its cost to the run, the same as a text call. */
class TracingGatewayTest {

    @Test
    void shouldRecordAnImageCallsSpanAndCostUnderTheServiceName() {
        List<String> spans = new CopyOnWriteArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                if ("mindforge.service".equals(context.getName())) {
                    spans.add(context.getContextualName());
                }
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        TracingGateway gateway = new TracingGateway(new StubAIGateway(), "ImageDescriber", registry);

        RunCost cost = new ObservationRunMeter(registry).measure(makeRun(), () ->
            gateway.complete(ModelTier.VISION, "describe", makeImage(), DeadlineProfile.BACKGROUND));

        assertThat(spans).containsExactly("ImageDescriber");
        assertThat(cost.byService()).containsExactly(Map.entry("ImageDescriber", StubAIGateway.COST_PER_CALL_USD));
    }

    private static ImageInput makeImage() {
        return new ImageInput(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "image/png");
    }

    private static IngestRun makeRun() {
        return new IngestRun(UUID.randomUUID(), UUID.randomUUID(), RunKind.INGEST, UUID.randomUUID(), null,
            RunStatus.RUNNING, 1, null, null, List.of(), false, 0, Map.of(), List.of(), Instant.EPOCH, null, null);
    }
}
