package dev.mindforge.slack;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QueryService;
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
    private static final String COMMAND_EXECUTOR = "slackCommandExecutor";

    /**
     * Built without a network call; Bolt checks the bot token when {@link SlackBot} starts the Socket Mode app. Every
     * {@link SlackCommand} is routed by its name through the one gate.
     */
    @Bean
    App slackApp(AppProperties properties, SlackCommandListener listener, ObjectProvider<SlackCommand> commands) {
        App app = new App(AppConfig.builder().singleTeamBotToken(properties.getSlack().getBotToken()).build());
        commands.orderedStream().forEach(command -> app.command(command.name(), listener));
        return app;
    }

    @Bean
    SlackCommandListener slackCommandListener(SlackCommandHandler handler) {
        return new SlackCommandListener(handler);
    }

    @Bean
    SlackCommandHandler slackCommandHandler(SlackAllowlist allowlist, SlackRateLimiter rateLimiter,
                                            ExternalIdentityService identities,
                                            ObjectProvider<SlackCommand> commands,
                                            @Qualifier(COMMAND_EXECUTOR) Executor executor) {
        return new SlackCommandHandler(allowlist, rateLimiter, identities, commands.orderedStream().toList(),
            executor);
    }

    /**
     * Identity resolution and the command run here, after the ack, because they block on the database and the model.
     * Not a default candidate, so no other {@code Executor} injection point sees it; closed after the socket closes.
     */
    @Bean(name = COMMAND_EXECUTOR, destroyMethod = "close", defaultCandidate = false)
    ExecutorService slackCommandExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    SlackKnowledgeBases slackKnowledgeBases(KnowledgeBaseService knowledgeBases) {
        return new SlackKnowledgeBases(knowledgeBases);
    }

    @Bean
    SlackAskCommand slackAskCommand(QueryService queryService, SlackKnowledgeBases knowledgeBases) {
        return new SlackAskCommand(queryService, knowledgeBases);
    }

    @Bean
    SlackAllowlist slackAllowlist(AppProperties properties) {
        return new SlackAllowlist(properties.getSlack().getAllowedTeamIds());
    }

    @Bean
    SlackRateLimiter slackRateLimiter(AppProperties properties) {
        AppProperties.RateLimits limits = properties.getRateLimits();
        return new SlackRateLimiter(limits.getRequests(), limits.getWindow(), Clock.systemUTC());
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
