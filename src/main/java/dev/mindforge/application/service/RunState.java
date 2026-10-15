package dev.mindforge.application.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.RunFailure;

/**
 * What a run accumulates while it generates: failures and the version of every model service it called. The one
 * writer of a run's {@code step_versions}.
 */
final class RunState {

    final IngestRun run;
    private final List<RunFailure> failures = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> versions = new ConcurrentHashMap<>();
    private final ProcessingSettings settings;

    RunState(IngestRun run, ProcessingSettings settings) {
        this.run = run;
        this.settings = settings;
    }

    UUID kbId() {
        return run.knowledgeBaseId();
    }

    void version(Class<?> service, String version, ModelTier tier) {
        versions.put(service.getSimpleName(), settings.stepVersion(version, tier));
    }

    void addFailure(RunFailure failure) {
        failures.add(failure);
    }

    void addFailures(List<RunFailure> more) {
        failures.addAll(more);
    }

    List<RunFailure> failures() {
        synchronized (failures) {
            return List.copyOf(failures);
        }
    }

    Map<String, String> versions() {
        return Map.copyOf(versions);
    }
}
