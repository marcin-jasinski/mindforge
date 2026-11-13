package dev.mindforge.infrastructure.ai;

import java.util.function.Supplier;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Wraps one model service's calls in an observation named after the service, under the run observation
 * {@link ObservationRunMeter} bound to this thread when there is one, and adds each call's cost to that run under
 * the service name.
 */
public class TracingGateway implements AIGateway {

    private final AIGateway delegate;
    private final String serviceName;
    private final ObservationRegistry registry;

    public TracingGateway(AIGateway delegate, String serviceName, ObservationRegistry registry) {
        this.delegate = delegate;
        this.serviceName = serviceName;
        this.registry = registry;
    }

    /** Names the span and cost entry after the model service, as its {@code step_versions} key is named. */
    public static TracingGateway forService(AIGateway delegate, Class<?> service, ObservationRegistry registry) {
        return new TracingGateway(delegate, service.getSimpleName(), registry);
    }

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline) {
        return traced(() -> delegate.complete(tier, prompt, deadline));
    }

    @Override
    public CompletionResult complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile deadline) {
        return traced(() -> delegate.complete(tier, prompt, image, deadline));
    }

    private CompletionResult traced(Supplier<CompletionResult> call) {
        ObservationRunMeter.Run run = ObservationRunMeter.CURRENT.get();
        Observation service = Observation.createNotStarted("mindforge.service", registry)
            .contextualName(serviceName)
            .lowCardinalityKeyValue("mindforge.service", serviceName);
        if (run != null) {
            service.parentObservation(run.observation());
        }
        CompletionResult result = service.observe(call);
        if (run != null && result.costUsd() != null) {
            run.add(serviceName, result.costUsd());
        }
        return result;
    }
}
