package dev.mindforge.integration.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.slack.api.bolt.App;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.infrastructure.config.AppProperties;
import dev.mindforge.slack.SlackAllowlist;
import dev.mindforge.slack.SlackAskCommand;
import dev.mindforge.slack.SlackBot;
import dev.mindforge.slack.SlackCall;
import dev.mindforge.slack.SlackCommand;
import dev.mindforge.slack.SlackCommandHandler;
import dev.mindforge.slack.SlackCommandListener;
import dev.mindforge.slack.SlackConfig;
import dev.mindforge.slack.SlackKnowledgeBases;
import dev.mindforge.slack.SlackRateLimiter;
import dev.mindforge.slack.SlackReply;
import dev.mindforge.slack.SocketModeAppFactory;

/**
 * Without both tokens, or in the {@code cli} profile, nothing Slack-related is built. With both, the Socket Mode app is
 * built with the app-level token and the Java-WebSocket backend (ADR 0037). Placeholder tokens; no network.
 */
@Tag("integration")
class SlackConfigTest {

    private static final String BOT_TOKEN = "mindforge.slack.bot-token=xoxb-placeholder";
    private static final String APP_TOKEN = "mindforge.slack.app-token=xapp-placeholder";
    private static final String COMMAND_EXECUTOR = "slackCommandExecutor";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(SlackConfig.class, PropertiesConfig.class)
        .withBean(ExternalIdentityService.class, () -> mock(ExternalIdentityService.class))
        .withBean(KnowledgeBaseService.class, () -> mock(KnowledgeBaseService.class))
        .withBean(QueryService.class, () -> mock(QueryService.class))
        .withPropertyValues("mindforge.security.jwt-secret=test-secret", "mindforge.slack.allowed-team-ids=T0HOME");

    // AC3

    @Test
    void noSlackBeanExistsWithoutTheTokens() {
        runner.withPropertyValues("mindforge.slack.bot-token=", "mindforge.slack.app-token=")
            .run(ctx -> assertThat(ctx).hasNotFailed()
                .doesNotHaveBean(SlackConfig.class)
                .doesNotHaveBean(SlackBot.class)
                .doesNotHaveBean(App.class)
                .doesNotHaveBean(SocketModeApp.class)
                .doesNotHaveBean(SlackAllowlist.class)
                .doesNotHaveBean(SlackRateLimiter.class)
                .doesNotHaveBean(SlackCommandHandler.class)
                .doesNotHaveBean(SlackKnowledgeBases.class)
                .doesNotHaveBean(SlackAskCommand.class)
                .doesNotHaveBean(COMMAND_EXECUTOR));
    }

    @Test
    void noSlackBeanExistsInTheCliProfileEvenWithBothTokens() {
        runner.withPropertyValues("spring.profiles.active=cli", BOT_TOKEN, APP_TOKEN)
            .run(ctx -> assertThat(ctx).hasNotFailed()
                .doesNotHaveBean(SlackConfig.class)
                .doesNotHaveBean(SlackBot.class)
                .doesNotHaveBean(App.class)
                .doesNotHaveBean(SocketModeApp.class)
                .doesNotHaveBean(SlackAllowlist.class)
                .doesNotHaveBean(SlackRateLimiter.class)
                .doesNotHaveBean(SlackCommandHandler.class)
                .doesNotHaveBean(SlackKnowledgeBases.class)
                .doesNotHaveBean(SlackAskCommand.class)
                .doesNotHaveBean(COMMAND_EXECUTOR));
    }

    @Test
    void onlyOneTokenFailsStartup() {
        runner.withPropertyValues(BOT_TOKEN)
            .run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("app-token must be set when bot-token is set"));
    }

    // AC3, AC4

    @Test
    void bothTokensBuildTheSocketModeAppWithoutOpeningASocket() {
        runner.withPropertyValues(BOT_TOKEN, APP_TOKEN)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed().hasSingleBean(SlackBot.class).hasSingleBean(App.class);
                SocketModeApp socketModeApp = ctx.getBean(SocketModeApp.class);
                assertThat(socketModeApp.getClient()).isNull();
                assertThat(socketModeApp.isClientStopped()).isTrue();
            });
    }

    // AC4, AC5

    @Test
    void bothTokensBuildAndStartTheSocketModeAppWithTheAppTokenAndTheJavaWebSocketBackend() throws Exception {
        SocketModeApp fake = mock(SocketModeApp.class);
        List<Object[]> calls = new ArrayList<>();
        SocketModeAppFactory factory = (appToken, backend, app) -> {
            calls.add(new Object[] {appToken, backend, app});
            return fake;
        };

        runner.withPropertyValues(BOT_TOKEN, APP_TOKEN)
            .withBean(SocketModeAppFactory.class, () -> factory)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx.getBean(SocketModeApp.class)).isSameAs(fake);
                assertThat(calls).singleElement().satisfies(call -> assertThat(call)
                    .containsExactly("xapp-placeholder", SocketModeClient.Backend.JavaWebSocket, ctx.getBean(App.class)));

                ctx.getBean(SlackBot.class).run(null);

                verify(fake).startAsync();
            });
        verify(fake).close();
    }

    // P19-03 AC10

    @Test
    void bothTokensBuildTheGate_andRouteEachSlackCommandByItsName() {
        runner.withPropertyValues(BOT_TOKEN, APP_TOKEN)
            .withBean(SlackCommand.class, FakeCommand::new)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed()
                    .hasSingleBean(SlackAllowlist.class)
                    .hasSingleBean(SlackRateLimiter.class)
                    .hasSingleBean(SlackCommandHandler.class)
                    .hasBean(COMMAND_EXECUTOR);
                assertThat(ctx.getBean(SlackAllowlist.class).teamIds()).containsExactly("T0HOME");
                @SuppressWarnings("unchecked")
                Map<Pattern, SlashCommandHandler> routes = (Map<Pattern, SlashCommandHandler>)
                    ReflectionTestUtils.getField(ctx.getBean(App.class), "slashCommandHandlers");
                assertThat(routes).hasSize(2)
                    .allSatisfy((pattern, listener) ->
                        assertThat(listener).isSameAs(ctx.getBean(SlackCommandListener.class)));
                assertThat(routes.keySet()).anySatisfy(
                    pattern -> assertThat(pattern.matcher(FakeCommand.NAME).matches()).isTrue());
            });
    }

    // P19-04 AC6

    @Test
    void bothTokensRegisterTheAskCommandAndTheKnowledgeBaseResolver() {
        runner.withPropertyValues(BOT_TOKEN, APP_TOKEN)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed()
                    .hasSingleBean(SlackKnowledgeBases.class)
                    .hasSingleBean(SlackAskCommand.class);
                @SuppressWarnings("unchecked")
                Map<Pattern, SlashCommandHandler> routes = (Map<Pattern, SlashCommandHandler>)
                    ReflectionTestUtils.getField(ctx.getBean(App.class), "slashCommandHandlers");
                assertThat(routes.entrySet()).singleElement().satisfies(route -> {
                    assertThat(route.getKey().matcher("/mf-ask").matches()).isTrue();
                    assertThat(route.getValue()).isSameAs(ctx.getBean(SlackCommandListener.class));
                });
            });
    }

    @Test
    void theCommandExecutor_runsOnVirtualThreadsAndIsClosedOnShutdown() throws Exception {
        ExecutorService[] executor = new ExecutorService[1];
        runner.withPropertyValues(BOT_TOKEN, APP_TOKEN)
            .run(ctx -> {
                executor[0] = ctx.getBean(COMMAND_EXECUTOR, ExecutorService.class);
                assertThat(executor[0].submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
            });
        assertThat(executor[0].isShutdown()).isTrue();
    }

    private static final class FakeCommand implements SlackCommand {

        static final String NAME = "/mf-fake";

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public void handle(SlackCall call, UUID userId, SlackReply reply) {
        }
    }

    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfig {
    }
}
