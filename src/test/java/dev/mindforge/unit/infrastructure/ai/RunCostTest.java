package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.application.service.IngestPipeline;
import dev.mindforge.application.service.LintService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.infrastructure.ai.ObservationRunMeter;
import dev.mindforge.infrastructure.ai.TracingGateway;
import dev.mindforge.support.StubAIGateway;
import io.micrometer.observation.ObservationRegistry;

/** A run's cost is summed over every model call of its fan-out threads, recorded, and warned about above the limit. */
class RunCostTest {

    private static final UUID KB = UUID.randomUUID();

    private final IngestRunRepository runs = mock(IngestRunRepository.class);
    private final IngestPipeline pipeline = mock(IngestPipeline.class);
    private final LintService lint = mock(LintService.class);
    private final StubAIGateway stub = new StubAIGateway();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger workerLog = (Logger) LoggerFactory.getLogger(RunWorker.class);

    RunCostTest() {
        logs.start();
        workerLog.addAppender(logs);
    }

    @AfterEach
    void detachLog() {
        workerLog.detachAppender(logs);
    }

    @Test
    void shouldSumCostAcrossForkedThreadsAndWarnAboveTheLimit() {
        AIGateway writer = new TracingGateway(stub, "PageWriter", ObservationRegistry.NOOP);
        AIGateway checker = new TracingGateway(stub, "LinkChecker", ObservationRegistry.NOOP);
        IngestRun run = makeQueued(RunKind.INGEST);
        doAnswer(invocation -> {
            try (ExecutorService fanOut = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 3; i++) {
                    fanOut.submit(() -> writer.complete(ModelTier.LARGE, "page", DeadlineProfile.BACKGROUND));
                }
                fanOut.submit(() -> checker.complete(ModelTier.SMALL, "links", DeadlineProfile.BACKGROUND));
            }
            return null;
        }).when(pipeline).run(run);

        drain(run, 0.0035);

        ArgumentCaptor<Double> cost = ArgumentCaptor.forClass(Double.class);
        verify(runs).recordCost(eq(KB), eq(run.runId()), cost.capture());
        assertThat(cost.getValue()).isCloseTo(0.004, within(1e-9));
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(run.runId().toString(), "PageWriter=0.003",
                "LinkChecker=0.001");
        });
    }

    @Test
    void shouldRecordNoCostAndNotWarnWhenALintMadeNoCall() {
        IngestRun run = makeQueued(RunKind.LINT);

        drain(run, 0.0);

        verify(lint).run(run);
        verify(runs).recordCost(eq(KB), eq(run.runId()), isNull());
        assertThat(logs.list).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void drain(IngestRun run, double costWarningUsd) {
        when(runs.oldestQueued(KB)).thenReturn(Optional.of(run), Optional.empty());
        when(runs.claim(KB, run.runId())).thenReturn(true);
        new RunWorker(runs, pipeline, lint, mock(ProgressNotifier.class), mock(EventPublisher.class),
            TransactionOperations.withoutTransaction(), Runnable::run,
            new ObservationRunMeter(ObservationRegistry.NOOP), costWarningUsd).drain(KB);
    }

    private static IngestRun makeQueued(RunKind kind) {
        return new IngestRun(UUID.randomUUID(), KB, kind, UUID.randomUUID(), null, RunStatus.QUEUED, 1, null, null,
            List.of(), false, 0, Map.of(), List.of(), Instant.EPOCH, null, null);
    }
}
