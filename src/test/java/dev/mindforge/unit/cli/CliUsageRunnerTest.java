package dev.mindforge.unit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import dev.mindforge.cli.CliUsageRunner;

class CliUsageRunnerTest {

    @Test
    void noCommandPrintsTheUsageAndReportsTheRefusedExitCode() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliUsageRunner runner = makeRunner(err);

        runner.run(new DefaultApplicationArguments("--spring.profiles.active=cli"));

        assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("Usage:");
        assertThat(runner.getExitCode()).isEqualTo(2);
    }

    @Test
    void anUnknownCommandPrintsTheUsageAndReportsTheRefusedExitCode() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliUsageRunner runner = makeRunner(err);

        runner.run(new DefaultApplicationArguments("--spring.profiles.active=cli", "foo"));

        assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("Usage:");
        assertThat(runner.getExitCode()).isEqualTo(2);
    }

    @Test
    void theKnownCommandsPrintNothingAndReportSuccess() {
        for (String command : new String[] {"pipeline", "quiz"}) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            CliUsageRunner runner = makeRunner(err);

            runner.run(new DefaultApplicationArguments("--spring.profiles.active=cli", command, "--kb", "kb-1"));

            assertThat(err.size()).as(command).isZero();
            assertThat(runner.getExitCode()).as(command).isZero();
        }
    }

    @Test
    void theUsageListsBothCommandsWithTheirFlagsAndTheThreeExitCodes() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        makeRunner(err).run(new DefaultApplicationArguments());

        assertThat(err.toString(StandardCharsets.UTF_8))
            .contains("pipeline --file=<path> --kb=<id> --user=<email> [--lesson-id=<id>] [--new-version]")
            .contains("quiz --kb=<id> --user=<email>")
            .contains("0  success")
            .contains("1  the ingest ran (or ran before) and failed")
            .contains("2  the input was refused before any run was queued");
    }

    private static CliUsageRunner makeRunner(ByteArrayOutputStream err) {
        return new CliUsageRunner(new PrintStream(err, true, StandardCharsets.UTF_8));
    }
}
