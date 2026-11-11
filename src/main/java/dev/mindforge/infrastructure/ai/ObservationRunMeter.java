package dev.mindforge.infrastructure.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunCost;
import dev.mindforge.domain.port.RunMeter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Opens the {@code mindforge.run} observation and binds it, with a cost accumulator, in an
 * {@link InheritableThreadLocal}: the run's fan-out threads inherit it and {@link TracingGateway} adds each call's
 * cost to it. Ceiling: a pooled thread created before the run does not see it; every pool a run uses is per run.
 */
public class ObservationRunMeter implements RunMeter {

    static final InheritableThreadLocal<Run> CURRENT = new InheritableThreadLocal<>();

    private final ObservationRegistry registry;

    public ObservationRunMeter(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public RunCost measure(IngestRun run, Runnable work) {
        Observation observation = Observation.createNotStarted("mindforge.run", registry)
            .lowCardinalityKeyValue("mindforge.run.kind", run.kind().name())
            .highCardinalityKeyValue("mindforge.run.id", run.runId().toString())
            .highCardinalityKeyValue("mindforge.kb.id", run.knowledgeBaseId().toString());
        Run current = new Run(observation, new ConcurrentHashMap<>());
        CURRENT.set(current);
        try {
            observation.observe(work);
        } finally {
            CURRENT.remove();
        }
        return RunCost.of(current.costs());
    }

    record Run(Observation observation, Map<String, Double> costs) {

        void add(String service, double usd) {
            costs.merge(service, usd, Double::sum);
        }
    }
}
