package dev.mindforge.discord;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import dev.mindforge.infrastructure.config.AppProperties;

/**
 * Wires the Discord bot adapter. Nothing here exists without {@code mindforge.discord.bot-token}, and nothing in the
 * {@code cli} profile, which exits after its own command.
 */
@Configuration
@Profile("!cli")
@Conditional(DiscordConfig.BotTokenSet.class)
public class DiscordConfig {

    private static final String BOT_TOKEN = "mindforge.discord.bot-token";
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

    /** Logs in in the background; {@link DiscordBot} waits for it and shuts it down. */
    @Bean(destroyMethod = "")
    JDA jda(AppProperties properties) {
        return JDABuilder.createLight(properties.getDiscord().getBotToken(), EnumSet.noneOf(GatewayIntent.class))
            .build();
    }

    @Bean(destroyMethod = "close")
    DiscordBot discordBot(JDA jda) {
        return new DiscordBot(jda, SHUTDOWN_WAIT);
    }

    @Bean
    DiscordAllowlist discordAllowlist(AppProperties properties) {
        AppProperties.Discord discord = properties.getDiscord();
        return new DiscordAllowlist(discord.getAllowedGuildIds(), discord.getAllowedRoleIds());
    }

    @Bean
    DiscordRateLimiter discordRateLimiter(AppProperties properties) {
        AppProperties.RateLimits limits = properties.getRateLimits();
        return new DiscordRateLimiter(limits.getRequests(), limits.getWindow(), Clock.systemUTC());
    }

    static class BotTokenSet implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(BOT_TOKEN));
        }
    }
}
