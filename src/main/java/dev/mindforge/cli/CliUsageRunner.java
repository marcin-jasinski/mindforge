package dev.mindforge.cli;

import java.io.PrintStream;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

/** Prints the usage to stderr and reports {@link CliExit#REFUSED} when the command is missing or unknown. */
public class CliUsageRunner implements ApplicationRunner, ExitCodeGenerator {

    static final String USAGE = """
        Usage: java -jar mindforge.jar --spring.profiles.active=cli <command> [options]

        Commands:
          pipeline --file=<path> --kb=<id> --user=<email> [--lesson-id=<id>] [--new-version]
          quiz --kb=<id> --user=<email>

        Exit codes:
          0  success
          1  the ingest ran (or ran before) and failed
          2  the input was refused before any run was queued (usage error, unknown user or knowledge base,
             not the owner, or a refused upload)
        """;

    private static final Set<String> COMMANDS = Set.of(CliCommand.PIPELINE, CliCommand.QUIZ);

    private final PrintStream err;
    private volatile CliExit exit = CliExit.OK;

    public CliUsageRunner(PrintStream err) {
        this.err = err;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (CliCommand.of(args).filter(COMMANDS::contains).isPresent()) {
            return;
        }
        err.print(USAGE);
        exit = CliExit.REFUSED;
    }

    @Override
    public int getExitCode() {
        return exit.code();
    }
}
