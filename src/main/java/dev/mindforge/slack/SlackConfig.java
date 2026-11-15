package dev.mindforge.slack;

import java.io.IOException;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import org.springframework.beans.factory.ObjectProvider;
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
 * Wires the Slack bot adapter (Socket Mode). Nothing here exists unless both {@code mindforge.slack.bot-token} and
 * {@code mindforge.slack.app-token} are set, and nothing in the {@code cli} profile, which exits after its own command.
 * The WebSocket client is Java-WebSocket (ADR 0037).
 */
@Configuration
@Profile("!cli")
@Conditional(SlackConfig.BothTokensSet.class)
public class SlackConfig {

    private static final String BOT_TOKEN = "mindforge.slack.bot-token";
    private static final String APP_TOKEN = "mindforge.slack.app-token";

    /** Built without a network call; Bolt checks the bot token when {@link SlackBot} starts the Socket Mode app. */
    @Bean
    App slackApp(AppProperties properties) {
        return new App(AppConfig.builder().singleTeamBotToken(properties.getSlack().getBotToken()).build());
    }

    /** {@link SlackBot} starts and closes it. */
    @Bean(destroyMethod = "")
    SocketModeApp socketModeApp(AppProperties properties, App slackApp, ObjectProvider<SocketModeAppFactory> factory)
        throws IOException {
        return factory.getIfAvailable(() -> SocketModeApp::new)
            .create(properties.getSlack().getAppToken(), SocketModeClient.Backend.JavaWebSocket, slackApp);
    }

    @Bean(destroyMethod = "close")
    SlackBot slackBot(SocketModeApp socketModeApp) {
        return new SlackBot(socketModeApp);
    }

    static class BothTokensSet implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(BOT_TOKEN))
                && StringUtils.hasText(context.getEnvironment().getProperty(APP_TOKEN));
        }
    }
}
