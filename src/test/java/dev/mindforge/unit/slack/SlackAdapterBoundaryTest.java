package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SlackAdapterBoundaryTest {

    private static final List<Path> SLACK_SOURCES = List.of(
        Path.of("src/main/java/dev/mindforge/slack"), Path.of("src/test/java/dev/mindforge/unit/slack"));
    private static final List<Path> BOLT_FREE = List.of(
        Path.of("src/main/java/dev/mindforge/slack/SlackAllowlist.java"),
        Path.of("src/main/java/dev/mindforge/slack/SlackRateLimiter.java"),
        Path.of("src/main/java/dev/mindforge/slack/SlackCall.java"));

    // AC11

    @Test
    void noSlackSource_importsTheDiscordAdapter() throws IOException {
        for (Path directory : SLACK_SOURCES) {
            assertThat(directory).isDirectory();
            try (Stream<Path> files = Files.walk(directory)) {
                assertThat(files.filter(file -> file.toString().endsWith(".java")))
                    .isNotEmpty()
                    .allSatisfy(file -> assertThat(Files.readString(file))
                        .doesNotContain("dev.mindforge" + ".discord"));
            }
        }
    }

    @Test
    void theAllowlistTheRateLimiterAndTheCallRecord_useNoBoltType() throws IOException {
        for (Path file : BOLT_FREE) {
            assertThat(Files.readString(file)).doesNotContain("com.slack.api");
        }
    }
}
