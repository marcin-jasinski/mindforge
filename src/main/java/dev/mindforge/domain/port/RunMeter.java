package dev.mindforge.domain.port;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunCost;

/** Traces a run's work and adds up what its model calls cost, the threads it forks included. */
public interface RunMeter {

    RunCost measure(IngestRun run, Runnable work);
}
