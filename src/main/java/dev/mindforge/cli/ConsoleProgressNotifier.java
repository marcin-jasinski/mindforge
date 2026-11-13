package dev.mindforge.cli;

import java.io.PrintStream;
import java.util.StringJoiner;
import java.util.UUID;

import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.port.ProgressNotifier;

/**
 * Prints one line per run progress message to the console. Once {@link #watch(UUID)} names the CLI's own run, the
 * progress of every other run is not printed. Best-effort: it never throws.
 */
public class ConsoleProgressNotifier implements ProgressNotifier {

    private static final int SHORT_ID_LENGTH = 8;

    private final PrintStream out;
    private volatile UUID watchedRunId;

    public ConsoleProgressNotifier(PrintStream out) {
        this.out = out;
    }

    /** Prints only the progress of this run from now on. */
    public void watch(UUID runId) {
        this.watchedRunId = runId;
    }

    @Override
    public void notify(UUID kbId, RunProgress progress) {
        try {
            UUID watched = watchedRunId;
            if (watched != null && !watched.equals(progress.runId())) {
                return;
            }
            out.println(line(progress));
        } catch (RuntimeException e) {
            // Best-effort: a console that cannot print must not fail the run.
        }
    }

    private static String line(RunProgress progress) {
        StringJoiner line = new StringJoiner(" ");
        line.add("[" + shortId(progress.runId()) + "]");
        if (progress.kind() != null) {
            line.add(progress.kind().name());
        }
        if (progress.step() != null && !progress.step().isBlank()) {
            line.add(progress.step());
        }
        if (progress.done() != null && progress.total() != null) {
            line.add(progress.done() + "/" + progress.total());
        }
        if (progress.status() != null) {
            line.add(progress.status().name());
        }
        return line.toString();
    }

    private static String shortId(UUID runId) {
        return runId == null ? "--------" : runId.toString().substring(0, SHORT_ID_LENGTH);
    }
}
