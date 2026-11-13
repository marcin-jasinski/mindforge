package dev.mindforge.integration.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.cli.CliConfig;
import dev.mindforge.cli.CliExitOnReady;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.infrastructure.config.AppProperties;

/** The exit listener exists by default in the {@code cli} profile only; the context runner never fires it. */
@Tag("integration")
class CliExitOnReadyWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CliConfig.class)
        .withBean(AccountService.class, () -> mock(AccountService.class))
        .withBean(KnowledgeBaseService.class, () -> mock(KnowledgeBaseService.class))
        .withBean(IngestionService.class, () -> mock(IngestionService.class))
        .withBean(IngestRunRepository.class, () -> mock(IngestRunRepository.class))
        .withBean(RunWorker.class, () -> mock(RunWorker.class))
        .withBean(AppProperties.class, AppProperties::new);

    @Test
    void theExitListenerExistsByDefaultInTheCliProfile() {
        runner.withPropertyValues("spring.profiles.active=cli")
            .run(ctx -> assertThat(ctx).hasSingleBean(CliExitOnReady.class));
    }

    @Test
    void theExitListenerIsAbsentWhenExitWhenDoneIsFalse() {
        runner.withPropertyValues("spring.profiles.active=cli", "mindforge.cli.exit-when-done=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(CliExitOnReady.class));
    }

    @Test
    void theExitListenerIsAbsentWithoutTheCliProfile() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CliExitOnReady.class));
    }
}
