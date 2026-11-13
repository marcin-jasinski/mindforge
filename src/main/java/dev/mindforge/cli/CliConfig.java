package dev.mindforge.cli;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.infrastructure.config.AppProperties;

/**
 * Wires the command-line adapter: in the {@code cli} profile run progress goes to the console, not to SSE; the first
 * non-option argument selects the command, and the process ends with the exit code the runners report.
 */
@Configuration
@Profile("cli")
public class CliConfig {

    private static final Duration RUN_POLL_INTERVAL = Duration.ofSeconds(1);

    @Bean
    ConsoleProgressNotifier progressNotifier() {
        return new ConsoleProgressNotifier(System.out);
    }

    @Bean
    CliUsageRunner cliUsageRunner() {
        return new CliUsageRunner(System.err);
    }

    @Bean
    PipelineCommandRunner pipelineCommandRunner(AccountService accounts, KnowledgeBaseService knowledgeBases,
                                                IngestionService ingestion, IngestRunRepository runs,
                                                RunWorker runWorker, ConsoleProgressNotifier progress,
                                                AppProperties properties) {
        return new PipelineCommandRunner(accounts, knowledgeBases, ingestion, runs, runWorker, progress,
            properties.getCli().getRunTimeout(), RUN_POLL_INTERVAL, System.out, System.err);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mindforge.cli", name = "exit-when-done", havingValue = "true",
        matchIfMissing = true)
    CliExitOnReady cliExitOnReady() {
        return new CliExitOnReady();
    }
}
