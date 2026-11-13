package dev.mindforge.unit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.cli.ConsoleProgressNotifier;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;

class ConsoleProgressNotifierTest {

    private static final UUID KB_ID = UUID.randomUUID();
    private static final UUID RUN_ID = UUID.fromString("1234abcd-0000-0000-0000-000000000001");
    private static final UUID OTHER_RUN_ID = UUID.fromString("9876fedc-0000-0000-0000-000000000002");

    @Test
    void aStepWithPageCountsPrintsShortRunIdKindStepCountsAndStatus() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        notifier(out).notify(KB_ID, makeStep(RUN_ID, "write", 3, 5));

        assertThat(lines(out)).containsExactly("[1234abcd] INGEST write 3/5 RUNNING");
    }

    @Test
    void aStatusChangeOmitsTheStepAndTheCounts() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        notifier(out).notify(KB_ID, makeStatus(RUN_ID, RunStatus.COMPLETED));

        assertThat(lines(out)).containsExactly("[1234abcd] INGEST COMPLETED");
    }

    @Test
    void theCountsArePrintedOnlyWhenBothAreSet() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        notifier(out).notify(KB_ID, makeStep(RUN_ID, "extract", 3, null));

        assertThat(lines(out)).containsExactly("[1234abcd] INGEST extract RUNNING");
    }

    @Test
    void everyRunIsPrintedBeforeARunIsWatched() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleProgressNotifier notifier = notifier(out);

        notifier.notify(KB_ID, makeStatus(RUN_ID, RunStatus.RUNNING));
        notifier.notify(KB_ID, makeStatus(OTHER_RUN_ID, RunStatus.RUNNING));

        assertThat(lines(out)).containsExactly("[1234abcd] INGEST RUNNING", "[9876fedc] INGEST RUNNING");
    }

    @Test
    void anotherRunIsNotPrintedOnceARunIsWatched() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleProgressNotifier notifier = notifier(out);

        notifier.watch(RUN_ID);
        notifier.notify(KB_ID, makeStatus(OTHER_RUN_ID, RunStatus.RUNNING));
        notifier.notify(KB_ID, makeStatus(RUN_ID, RunStatus.RUNNING));

        assertThat(lines(out)).containsExactly("[1234abcd] INGEST RUNNING");
    }

    @Test
    void aFailingConsoleDoesNotThrow() {
        PrintStream failing = new PrintStream(OutputStream.nullOutputStream()) {
            @Override
            public void println(String line) {
                throw new IllegalStateException("console closed");
            }
        };
        ConsoleProgressNotifier notifier = new ConsoleProgressNotifier(failing);

        assertThatCode(() -> notifier.notify(KB_ID, makeStatus(RUN_ID, RunStatus.RUNNING)))
            .doesNotThrowAnyException();
    }

    @Test
    void aNullProgressDoesNotThrow() {
        ConsoleProgressNotifier notifier = notifier(new ByteArrayOutputStream());

        assertThatCode(() -> notifier.notify(KB_ID, null)).doesNotThrowAnyException();
        notifier.watch(RUN_ID);
        assertThatCode(() -> notifier.notify(KB_ID, null)).doesNotThrowAnyException();
    }

    @Test
    void aProgressWithNullFieldsDoesNotThrow() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleProgressNotifier notifier = notifier(out);

        assertThatCode(() -> notifier.notify(KB_ID, new RunProgress(null, null, null, null, null, null, null, null)))
            .doesNotThrowAnyException();
        assertThat(lines(out)).containsExactly("[--------]");
    }

    private static ConsoleProgressNotifier notifier(ByteArrayOutputStream out) {
        return new ConsoleProgressNotifier(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static String[] lines(ByteArrayOutputStream out) {
        String text = out.toString(StandardCharsets.UTF_8);
        return text.isEmpty() ? new String[0] : text.split("\\R");
    }

    private static RunProgress makeStep(UUID runId, String step, Integer done, Integer total) {
        return new RunProgress(runId, RunKind.INGEST, UUID.randomUUID(), RunStatus.RUNNING, step, done, total,
            Instant.now());
    }

    private static RunProgress makeStatus(UUID runId, RunStatus status) {
        return new RunProgress(runId, RunKind.INGEST, UUID.randomUUID(), status, null, null, null, Instant.now());
    }
}
