package dev.mindforge.unit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import dev.mindforge.cli.CliCommand;

class CliCommandTest {

    @Test
    void theCommandIsTheFirstNonOptionArgument() {
        assertThat(CliCommand.of(new DefaultApplicationArguments(
                "--spring.profiles.active=cli", "pipeline", "--file", "notes.md", "quiz")))
            .contains("pipeline");
    }

    @Test
    void thereIsNoCommandWithoutANonOptionArgument() {
        assertThat(CliCommand.of(new DefaultApplicationArguments("--spring.profiles.active=cli"))).isEmpty();
    }
}
