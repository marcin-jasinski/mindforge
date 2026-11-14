package dev.mindforge.integration.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.mindforge.discord.DiscordAllowlist;
import dev.mindforge.discord.DiscordBot;
import dev.mindforge.discord.DiscordConfig;
import dev.mindforge.discord.DiscordRateLimiter;
import dev.mindforge.infrastructure.config.AppProperties;
import net.dv8tion.jda.api.JDA;

/** Without a bot token, or in the {@code cli} profile, nothing Discord-related is built and no login is tried. */
@Tag("integration")
class DiscordConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(DiscordConfig.class)
        .withBean(AppProperties.class, AppProperties::new);

    // AC6

    @Test
    void noDiscordBeanExistsWithoutABotToken() {
        runner.withPropertyValues("mindforge.discord.bot-token=")
            .run(ctx -> assertThat(ctx).hasNotFailed()
                .doesNotHaveBean(JDA.class)
                .doesNotHaveBean(DiscordBot.class)
                .doesNotHaveBean(DiscordAllowlist.class)
                .doesNotHaveBean(DiscordRateLimiter.class));
    }

    @Test
    void noDiscordBeanExistsInTheCliProfileEvenWithABotToken() {
        runner.withPropertyValues("spring.profiles.active=cli", "mindforge.discord.bot-token=token")
            .run(ctx -> assertThat(ctx).hasNotFailed()
                .doesNotHaveBean(JDA.class)
                .doesNotHaveBean(DiscordBot.class));
    }
}
