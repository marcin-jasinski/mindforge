package dev.mindforge.discord;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.Executors;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.FlashcardService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.ReminderService;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.infrastructure.config.AppProperties;
import dev.mindforge.infrastructure.event.DueReminderTrigger;

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

    /**
     * Logs in in the background; {@link DiscordBot} waits for it and shuts it down. Events run on virtual threads,
     * because commands block on the model and on JDA's {@code complete()}.
     */
    @Bean(destroyMethod = "")
    JDA jda(AppProperties properties) {
        return JDABuilder.createLight(properties.getDiscord().getBotToken(), EnumSet.noneOf(GatewayIntent.class))
            .setEventPool(Executors.newVirtualThreadPerTaskExecutor(), true)
            .build();
    }

    @Bean(destroyMethod = "close")
    DiscordBot discordBot(JDA jda, DiscordListener listener, ObjectProvider<DiscordCommand> commands) {
        return new DiscordBot(jda, listener, commands.orderedStream().toList(), SHUTDOWN_WAIT);
    }

    @Bean
    DiscordListener discordListener(DiscordCommandHandler handler, DiscordKnowledgeBases knowledgeBases) {
        return new DiscordListener(handler, knowledgeBases);
    }

    @Bean
    DiscordCommandHandler discordCommandHandler(DiscordAllowlist allowlist, DiscordRateLimiter rateLimiter,
                                                ExternalIdentityService identities,
                                                ObjectProvider<DiscordCommand> commands) {
        return new DiscordCommandHandler(allowlist, rateLimiter, identities, commands.orderedStream().toList());
    }

    @Bean
    DiscordKnowledgeBases discordKnowledgeBases(DiscordAllowlist allowlist, ExternalIdentityService identities,
                                                KnowledgeBaseService knowledgeBases) {
        return new DiscordKnowledgeBases(allowlist, identities, knowledgeBases);
    }

    @Bean
    AskCommand askCommand(QueryService queryService, DiscordKnowledgeBases knowledgeBases) {
        return new AskCommand(queryService, knowledgeBases);
    }

    @Bean
    QuizCommand quizCommand(QuizService quizService, DiscordKnowledgeBases knowledgeBases,
                            DiscordQuizSessions quizSessions) {
        return new QuizCommand(quizService, knowledgeBases, quizSessions);
    }

    @Bean
    UploadCommand uploadCommand(IngestionService ingestionService, DiscordKnowledgeBases knowledgeBases,
                                AppProperties properties) {
        return new UploadCommand(ingestionService, knowledgeBases, new DiscordAttachments(),
            properties.getUpload().getMaxSize().toBytes());
    }

    @Bean
    DiscordQuizSessions discordQuizSessions(AppProperties properties) {
        return new DiscordQuizSessions(properties.getStudy().getQuizSessionTtl());
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

    @Bean
    DueReminderTrigger dueReminderTrigger(ExternalIdentityRepository identities, KnowledgeBaseService knowledgeBases,
                                          FlashcardService flashcards, JDA jda) {
        return new DueReminderTrigger(new ReminderService(identities, knowledgeBases, flashcards,
            new JdaReminderSender(jda)));
    }

    static class BotTokenSet implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(BOT_TOKEN));
        }
    }
}
