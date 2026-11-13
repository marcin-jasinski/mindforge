package dev.mindforge.integration.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.mindforge.cli.CliConfig;
import dev.mindforge.cli.CliExitOnReady;

/** The exit listener exists by default in the {@code cli} profile only; the context runner never fires it. */
@Tag("integration")
class CliExitOnReadyWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CliConfig.class);

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
