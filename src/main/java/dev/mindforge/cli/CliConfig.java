package dev.mindforge.cli;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the command-line adapter: in the {@code cli} profile run progress goes to the console, not to SSE; the first
 * non-option argument selects the command, and the process ends with the exit code the runners report.
 */
@Configuration
@Profile("cli")
public class CliConfig {

    @Bean
    ConsoleProgressNotifier progressNotifier() {
        return new ConsoleProgressNotifier(System.out);
    }

    @Bean
    CliUsageRunner cliUsageRunner() {
        return new CliUsageRunner(System.err);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mindforge.cli", name = "exit-when-done", havingValue = "true",
        matchIfMissing = true)
    CliExitOnReady cliExitOnReady() {
        return new CliExitOnReady();
    }
}
