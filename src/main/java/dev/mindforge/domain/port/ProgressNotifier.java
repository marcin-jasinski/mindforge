package dev.mindforge.domain.port;

import java.util.UUID;

import dev.mindforge.domain.model.RunProgress;

/**
 * Streams run progress to whoever watches a knowledge base. Best-effort: it never throws, and a missed message is
 * repaired by reading the runs, never redelivered. A status change is notified only after its transaction returns.
 */
public interface ProgressNotifier {

    void notify(UUID kbId, RunProgress progress);
}
