package dev.mindforge.domain.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunKind;

/**
 * Ingest runs and the knowledge base's lease ({@code active_run_id}). Every status change after the claim is
 * fenced: it matches only a run in the expected status that holds the lease, and otherwise throws
 * {@link RunFencedException} so the caller's transaction rolls back.
 */
public interface IngestRunRepository {

    /** Inserts a {@code QUEUED} run. */
    IngestRun enqueue(UUID kbId, IngestRun run);

    /**
     * Takes the lease and moves the run from {@code QUEUED} to {@code RUNNING}, together. Returns false, having
     * changed nothing, when another run holds the lease or this run is not queued. A caller that cannot wait
     * throws {@link KnowledgeBaseBusyException}.
     */
    boolean claim(UUID kbId, UUID runId);

    Optional<IngestRun> oldestQueued(UUID kbId);

    /** {@code RUNNING → WRITTEN}, recording the run's failures and step versions so far. */
    void markWritten(UUID kbId, UUID runId, List<Map<String, Object>> failures, Map<String, String> stepVersions);

    /** {@code WRITTEN → COMPLETED}, releasing the lease. */
    void complete(UUID kbId, UUID runId, int supersessionCount, boolean supersessionSkipped,
                  List<Map<String, Object>> failures, Map<String, String> stepVersions);

    /** {@code RUNNING → FAILED}, releasing the lease. */
    void fail(UUID kbId, UUID runId, String reason, boolean retryable, List<Map<String, Object>> failures,
              Map<String, String> stepVersions);

    /** Stores a Lint's findings and suggestions; call inside the run's fenced commit. */
    void recordFindings(UUID kbId, UUID runId, List<Map<String, Object>> findings);

    Optional<IngestRun> findById(UUID kbId, UUID runId);

    Optional<IngestRun> latestForDocument(UUID kbId, UUID documentId);

    /** The newest run of each document in the knowledge base. */
    List<IngestRun> latestPerDocument(UUID kbId);

    Optional<IngestRun> latestCompleted(UUID kbId, RunKind kind);

    /** Whether a run of this kind is queued, running or written. */
    boolean hasQueuedOrActive(UUID kbId, RunKind kind);

    // ---------------------------------------------------------------------------
    // System methods: for the sweep only, which acts for no user
    // ---------------------------------------------------------------------------

    /** Runs {@code RUNNING} or {@code WRITTEN} in any knowledge base; each carries its own. */
    List<IngestRun> findUnfinished();

    List<UUID> knowledgeBasesWithQueuedRuns();
}
