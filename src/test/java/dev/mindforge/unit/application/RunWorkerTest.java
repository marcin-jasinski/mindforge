package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.IngestPipeline;
import dev.mindforge.application.service.LintService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;

class RunWorkerTest {

    private static final UUID KB = UUID.randomUUID();

    private final IngestRunRepository runs = mock(IngestRunRepository.class);
    private final IngestPipeline pipeline = mock(IngestPipeline.class);
    private final LintService lint = mock(LintService.class);
    private final EventPublisher events = mock(EventPublisher.class);

    @Test
    void shouldRunTheOldestQueuedRunAndThenTheNextOne() {
        IngestRun first = makeRun(RunKind.INGEST, RunStatus.QUEUED, 1);
        IngestRun second = makeRun(RunKind.INGEST, RunStatus.QUEUED, 1);
        when(runs.oldestQueued(KB)).thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
        when(runs.claim(eq(KB), any())).thenReturn(true);

        makeWorker(Runnable::run).drain(KB);

        verify(pipeline).run(first);
        verify(pipeline).run(second);
    }

    @Test
    void shouldRunALintQueuedBehindAnIngestNext() {
        IngestRun ingest = makeRun(RunKind.INGEST, RunStatus.QUEUED, 1);
        IngestRun lintRun = makeRun(RunKind.LINT, RunStatus.QUEUED, 1);
        when(runs.oldestQueued(KB)).thenReturn(Optional.of(ingest), Optional.of(lintRun), Optional.empty());
        when(runs.claim(eq(KB), any())).thenReturn(true);

        makeWorker(Runnable::run).drain(KB);

        InOrder order = inOrder(pipeline, lint);
        order.verify(pipeline).run(ingest);
        order.verify(lint).run(lintRun);
    }

    @Test
    void shouldLeaveARunQueuedWhenItsClaimIsLost() {
        IngestRun queued = makeRun(RunKind.INGEST, RunStatus.QUEUED, 1);
        when(runs.oldestQueued(KB)).thenReturn(Optional.of(queued));
        RunWorker worker = makeWorker(Runnable::run);

        worker.drain(KB);
        verify(pipeline, never()).run(any());

        when(runs.claim(KB, queued.runId())).thenReturn(true, false);
        worker.drain(KB);
        verify(pipeline).run(queued);
    }

    @Test
    void shouldCompleteAWrittenRunWithoutSupersessionsAndFailARunningOneAsInterrupted() {
        IngestRun written = makeRun(RunKind.INGEST, RunStatus.WRITTEN, 1);
        IngestRun lint = makeRun(RunKind.LINT, RunStatus.RUNNING, 1);
        when(runs.findUnfinished()).thenReturn(List.of(written, lint));

        makeWorker(Runnable::run).sweep();

        verify(runs).complete(KB, written.runId(), 0, true, List.of(), Map.of());
        verify(runs).fail(KB, lint.runId(), "interrupted", true, List.of(), Map.of());
        verify(runs, never()).enqueue(any(), any());
    }

    @Test
    void shouldRequeueAnInterruptedIngestAsItsNextAttemptAtMostTwice() {
        IngestRun second = makeRun(RunKind.INGEST, RunStatus.RUNNING, 2);
        IngestRun third = makeRun(RunKind.INGEST, RunStatus.RUNNING, 3);
        when(runs.findUnfinished()).thenReturn(List.of(second, third));
        when(runs.enqueue(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        makeWorker(Runnable::run).sweep();

        ArgumentCaptor<IngestRun> requeued = ArgumentCaptor.forClass(IngestRun.class);
        verify(runs).enqueue(eq(KB), requeued.capture());
        assertThat(requeued.getValue()).extracting(IngestRun::documentId, IngestRun::attempt, IngestRun::status)
            .containsExactly(second.documentId(), 3, RunStatus.QUEUED);
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(DomainEvent.IngestRunQueued.class,
            event -> assertThat(event.runId()).isEqualTo(requeued.getValue().runId()));
    }

    @Test
    void shouldNotSweepARunThisProcessIsExecuting() {
        IngestRun queued = makeRun(RunKind.INGEST, RunStatus.QUEUED, 1);
        when(runs.oldestQueued(KB)).thenReturn(Optional.of(queued));
        when(runs.claim(KB, queued.runId())).thenReturn(true);
        List<Runnable> started = new ArrayList<>();
        RunWorker worker = makeWorker(started::add);
        worker.drain(KB);
        when(runs.findUnfinished()).thenReturn(List.of(makeRun(queued.runId(), RunStatus.RUNNING)));

        worker.sweep();

        verify(runs, never()).fail(any(), any(), any(), anyBoolean(), any(), any());
        verify(runs, never()).complete(any(), any(), anyInt(), anyBoolean(), any(), any());
    }

    @Test
    void shouldStopClaimingOnceStopped() {
        RunWorker worker = makeWorker(Runnable::run);

        worker.stop();
        worker.drain(KB);

        verify(runs, never()).oldestQueued(any());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private RunWorker makeWorker(Executor executor) {
        return new RunWorker(runs, pipeline, lint, mock(ProgressNotifier.class), events,
            TransactionOperations.withoutTransaction(), executor);
    }

    private static IngestRun makeRun(RunKind kind, RunStatus status, int attempt) {
        return new IngestRun(UUID.randomUUID(), KB, kind, UUID.randomUUID(), null, status, attempt, null, null,
            List.of(), false, 0, Map.of(), List.of(), Instant.EPOCH, null, null);
    }

    private static IngestRun makeRun(UUID runId, RunStatus status) {
        return new IngestRun(runId, KB, RunKind.INGEST, UUID.randomUUID(), null, status, 1, null, null,
            List.of(), false, 0, Map.of(), List.of(), Instant.EPOCH, null, null);
    }
}
