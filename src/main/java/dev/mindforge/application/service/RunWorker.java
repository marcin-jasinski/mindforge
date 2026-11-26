package dev.mindforge.application.service;

import java.time.Duration;
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
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunNotCancellableException;
import dev.mindforge.domain.model.RunCost;
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.RunMeter;

/**
 * Claims the oldest {@code QUEUED} run of a knowledge base and runs it, and sweeps runs this process is not running
 * (T17). A run id joins the active set before its claim and leaves it after its terminal transaction, so the sweep
 * never mistakes a claimed run for an abandoned one. Ceiling: one live instance.
 */
public class RunWorker {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final String INTERRUPTED = "interrupted";
    /** The failure reason of a run its user stopped; such a run is never retried. */
    public static final String CANCELLED_REASON = "cancelled by the user";

    private final IngestRunRepository runs;
    private final IngestPipeline pipeline;
    private final LintService lint;
    private final ProgressNotifier progress;
    private final EventPublisher events;
    private final TransactionOperations transactions;
    private final Executor executor;
    private final RunMeter meter;
    private final double costWarningUsd;
    private final Duration retryDelay;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    /** The thread executing each run, so a stop can interrupt the model call it is waiting on. */
    private final Map<UUID, Thread> executing = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    public RunWorker(IngestRunRepository runs, IngestPipeline pipeline, LintService lint, ProgressNotifier progress,
                     EventPublisher events, TransactionOperations transactions, Executor executor, RunMeter meter,
                     double costWarningUsd, Duration retryDelay) {
        this.runs = runs;
        this.pipeline = pipeline;
        this.lint = lint;
        this.progress = progress;
        this.events = events;
        this.transactions = transactions;
        this.executor = executor;
        this.meter = meter;
        this.costWarningUsd = costWarningUsd;
        this.retryDelay = retryDelay;
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
            executing.put(run.runId(), Thread.currentThread());
            try {
                dispatch(run);
            } catch (RuntimeException e) {
                log.error("Run {} ended abnormally; the sweep settles it", run.runId(), e);
            } finally {
                executing.remove(run.runId());
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

    /**
     * Stops a queued or running ingest or lint run before it writes pages. A queued run is failed in place. A running
     * one is failed under its lease, so its next fenced commit finds nothing to write, and its thread is interrupted so
     * that it stops waiting for the model. A model call already sent to the server runs to its end there.
     *
     * @throws RunNotCancellableException when the run is a revert, is not queued or running, or already writes pages
     */
    public void cancel(UUID kbId, UUID runId) {
        IngestRun run = runs.findById(kbId, runId).orElseThrow(() -> new NotFoundException("run"));
        if (run.kind() == RunKind.REVERT) {
            throw new RunNotCancellableException(runId);
        }
        try {
            switch (run.status()) {
                case QUEUED -> {
                    if (!runs.cancelQueued(kbId, runId, CANCELLED_REASON)) {
                        throw new RunNotCancellableException(runId);
                    }
                }
                case RUNNING -> transactions.execute(status -> {
                    runs.fail(kbId, runId, CANCELLED_REASON, false, run.failures(), run.stepVersions());
                    return null;
                });
                default -> throw new RunNotCancellableException(runId);
            }
        } catch (RunFencedException e) {
            throw new RunNotCancellableException(runId);
        }
        log.info("Run {} stopped by its user", runId);
        Optional.ofNullable(executing.get(runId)).ifPresent(Thread::interrupt);
        progress.notify(kbId, RunProgress.status(run, RunStatus.FAILED));
    }

    /** Stops claiming; runs already executing finish. */
    public void stop() {
        stopped = true;
    }

    private void dispatch(IngestRun run) {
        switch (run.kind()) {
            case INGEST -> {
                recordCost(run, meter.measure(run, () -> pipeline.run(run)));
                requeueTransientFailure(run);
            }
            case LINT -> recordCost(run, meter.measure(run, () -> lint.run(run)));
            // a revert is one synchronous transaction and is never queued
            case REVERT -> runs.fail(run.knowledgeBaseId(), run.runId(), "a revert is never queued", false,
                List.of(), Map.of());
        }
    }

    /**
     * Queues the next attempt of an ingest that failed on a transient model error, up to {@link #MAX_ATTEMPTS}. The
     * pause happens after the lease is released, so other runs of the knowledge base are not blocked by it.
     */
    private void requeueTransientFailure(IngestRun run) {
        UUID kbId = run.knowledgeBaseId();
        if (run.attempt() >= MAX_ATTEMPTS) {
            return;
        }
        Optional<IngestRun> failed = runs.findById(kbId, run.runId());
        if (failed.isEmpty() || failed.get().status() != RunStatus.FAILED
            || !Boolean.TRUE.equals(failed.get().retryable())) {
            return;
        }
        pause();
        IngestRun retry = transactions.execute(status -> {
            IngestRun queued = runs.enqueue(kbId, new IngestRun(UUID.randomUUID(), kbId, RunKind.INGEST,
                run.documentId(), null, RunStatus.QUEUED, run.attempt() + 1, null, null, List.of(), false, 0,
                Map.of(), List.of(), null, null, null));
            events.publish(new DomainEvent.IngestRunQueued(queued.runId(), kbId, Instant.now()));
            return queued;
        });
        log.warn("Run {} failed on a transient error; queued attempt {} as run {}", run.runId(),
            run.attempt() + 1, retry.runId());
        progress.notify(kbId, RunProgress.status(retry, RunStatus.QUEUED));
    }

    private void pause() {
        if (retryDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Cost is telemetry, not wiki state: its own update after the run's terminal commit, whatever that was. */
    private void recordCost(IngestRun run, RunCost cost) {
        runs.recordCost(run.knowledgeBaseId(), run.runId(), cost.totalUsd());
        if (cost.totalUsd() != null && cost.totalUsd() > costWarningUsd) {
            log.warn("Run {} cost {} USD, above {} USD: {}", run.runId(), cost.totalUsd(), costWarningUsd,
                cost.byService());
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
