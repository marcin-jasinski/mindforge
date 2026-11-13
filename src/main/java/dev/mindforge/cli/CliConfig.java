package dev.mindforge.cli;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Wires the command-line adapter: in the {@code cli} profile run progress goes to the console, not to SSE. */
@Configuration
@Profile("cli")
public class CliConfig {

    @Bean
    ConsoleProgressNotifier progressNotifier() {
        return new ConsoleProgressNotifier(System.out);
    }
}
