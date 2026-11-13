package dev.mindforge.cli;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;

/** Reads the CLI command: the first non-option argument. */
public final class CliCommand {

    public static final String PIPELINE = "pipeline";
    public static final String QUIZ = "quiz";

    private CliCommand() {
    }

    /** Returns the first non-option argument, or empty when there is none. */
    public static Optional<String> of(ApplicationArguments args) {
        List<String> nonOptionArgs = args.getNonOptionArgs();
        return nonOptionArgs.isEmpty() ? Optional.empty() : Optional.of(nonOptionArgs.getFirst());
    }
}
