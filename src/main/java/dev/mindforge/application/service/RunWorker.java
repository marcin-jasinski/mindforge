package dev.mindforge.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;

/**
 * Claims the oldest {@code QUEUED} run of a knowledge base and runs it, and sweeps runs this process is not running
 * (T17). A run id joins the active set before its claim and leaves it after its terminal transaction, so the sweep
 * never mistakes a claimed run for an abandoned one. Ceiling: one live instance.
 */
public class RunWorker {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final String INTERRUPTED = "interrupted";

    private final IngestRunRepository runs;
    private final IngestPipeline pipeline;
    private final ProgressNotifier progress;
    private final EventPublisher events;
    private final TransactionOperations transactions;
    private final Executor executor;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;

    public RunWorker(IngestRunRepository runs, IngestPipeline pipeline, ProgressNotifier progress,
                     EventPublisher events, TransactionOperations transactions, Executor executor) {
        this.runs = runs;
        this.pipeline = pipeline;
        this.progress = progress;
        this.events = events;
        this.transactions = transactions;
        this.executor = executor;
    }

    /** Starts the oldest queued run of the knowledge base, unless another run holds its lease. */
    public void drain(UUID kbId) {
        if (stopped) {
            return;
        }
        Optional<IngestRun> next = runs.oldestQueued(kbId);
        if (next.isEmpty() || !active.add(next.get().runId())) {
            return;
        }
        IngestRun run = next.get();
        boolean claimed = false;
        try {
            claimed = runs.claim(kbId, run.runId());
        } finally {
            if (!claimed) {
                active.remove(run.runId());
            }
        }
        if (!claimed) {
            return;
        }
        progress.notify(kbId, RunProgress.status(run, RunStatus.RUNNING));
        executor.execute(() -> {
            try {
                dispatch(run);
            } catch (RuntimeException e) {
                log.error("Run {} ended abnormally; the sweep settles it", run.runId(), e);
            } finally {
                active.remove(run.runId());
                drain(kbId);
            }
        });
    }

    /**
     * Settles every {@code RUNNING} or {@code WRITTEN} run not executing here — a written run completes without
     * supersessions, a running one fails as interrupted and an ingest is re-queued up to three attempts — then drains
     * every knowledge base with queued runs.
     */
    public void sweep() {
        for (IngestRun run : runs.findUnfinished()) {
            if (active.contains(run.runId())) {
                continue;
            }
            try {
                settle(run);
            } catch (RunFencedException e) {
                log.warn("Sweep left run {}: {}", run.runId(), e.getMessage());
            }
        }
        runs.knowledgeBasesWithQueuedRuns().forEach(this::drain);
    }

    /** Stops claiming; runs already executing finish. */
    public void stop() {
        stopped = true;
    }

    private void dispatch(IngestRun run) {
        switch (run.kind()) {
            case INGEST -> pipeline.run(run);
            case LINT, REVERT -> runs.fail(run.knowledgeBaseId(), run.runId(), "no worker runs a " + run.kind(),
                false, List.of(), Map.of());
        }
    }

    private void settle(IngestRun run) {
        UUID kbId = run.knowledgeBaseId();
        if (run.status() == RunStatus.WRITTEN) {
            runs.complete(kbId, run.runId(), run.supersessionCount(), true, run.failures(), run.stepVersions());
            progress.notify(kbId, RunProgress.status(run, RunStatus.COMPLETED));
            return;
        }
        Optional<IngestRun> requeued = transactions.execute(status -> {
            runs.fail(kbId, run.runId(), INTERRUPTED, true, run.failures(), run.stepVersions());
            if (run.kind() != RunKind.INGEST || run.attempt() >= MAX_ATTEMPTS) {
                return Optional.<IngestRun>empty();
            }
            IngestRun retry = runs.enqueue(kbId, new IngestRun(UUID.randomUUID(), kbId, RunKind.INGEST,
                run.documentId(), null, RunStatus.QUEUED, run.attempt() + 1, null, null, List.of(), false, 0,
                Map.of(), List.of(), null, null, null));
            events.publish(new DomainEvent.IngestRunQueued(retry.runId(), kbId, Instant.now()));
            return Optional.of(retry);
        });
        progress.notify(kbId, RunProgress.status(run, RunStatus.FAILED));
        requeued.ifPresent(retry -> progress.notify(kbId, RunProgress.status(retry, RunStatus.QUEUED)));
    }
}
