package dev.mindforge.infrastructure.event;

import java.util.concurrent.Executor;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.DomainEvent;

/**
 * Wakes the {@link RunWorker}: after a transaction that queued a run commits, at startup, every sweep interval; and
 * stops it claiming when the context closes. A drain runs on its own thread, outside the committed transaction.
 */
public class RunWorkerTriggers {

    private final RunWorker worker;
    private final Executor executor;

    public RunWorkerTriggers(RunWorker worker, Executor executor) {
        this.worker = worker;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunQueued(DomainEvent.IngestRunQueued event) {
        executor.execute(() -> worker.drain(event.knowledgeBaseId()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        executor.execute(worker::sweep);
    }

    @Scheduled(fixedDelayString = "${mindforge.runs.sweep-interval:PT1M}",
        initialDelayString = "${mindforge.runs.sweep-interval:PT1M}")
    public void sweepPeriodically() {
        worker.sweep();
    }

    @EventListener(ContextClosedEvent.class)
    public void onClose() {
        worker.stop();
    }
}
