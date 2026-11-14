package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.discord.DiscordAllowlist;
import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordCommand;
import dev.mindforge.discord.DiscordCommandHandler;
import dev.mindforge.discord.DiscordRateLimiter;
import dev.mindforge.discord.DiscordReply;
import dev.mindforge.domain.model.ExternalPlatform;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

class DiscordCommandHandlerTest {

    private static final long GUILD = 100L;
    private static final long ROLE = 200L;
    private static final long DISCORD_USER = 300L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String NOT_AVAILABLE = "This bot is not available here.";
    private static final String FAILED = "Something went wrong. Try again later.";

    private final DiscordRateLimiter rateLimiter = mock(DiscordRateLimiter.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final RecordingDiscordReply reply = new RecordingDiscordReply();

    // AC1

    @Test
    void aCommandFromAGuildNotOnTheAllowlist_isRejectedBeforeAnythingElse() {
        FakeCommand command = new FakeCommand();

        makeHandler(Set.of(), command).handle(makeCall(999L, Set.of()), reply);

        assertThat(reply.events).containsExactly(NOT_AVAILABLE);
        verifyNoInteractions(rateLimiter, identities);
        assertThat(command.calls).isEmpty();
    }

    // AC2

    @Test
    void aCommandFromADm_isRejectedTheSameWay() {
        FakeCommand command = new FakeCommand();

        makeHandler(Set.of(), command).handle(makeCall(null, Set.of()), reply);

        assertThat(reply.events).containsExactly(NOT_AVAILABLE);
        verifyNoInteractions(rateLimiter, identities);
        assertThat(command.calls).isEmpty();
    }

    @Test
    void aCommandFromAMemberWithoutARequiredRole_isRejectedTheSameWay() {
        FakeCommand command = new FakeCommand();

        makeHandler(Set.of(ROLE), command).handle(makeCall(GUILD, Set.of(ROLE + 1)), reply);

        assertThat(reply.events).containsExactly(NOT_AVAILABLE);
        verifyNoInteractions(rateLimiter, identities);
        assertThat(command.calls).isEmpty();
    }

    // AC3

    @Test
    void aCommandOverTheRateLimit_getsTheRetrySecondsAndResolvesNoUser() {
        when(rateLimiter.tryAcquire(DISCORD_USER)).thenReturn(OptionalLong.of(17));
        FakeCommand command = new FakeCommand();

        makeHandler(Set.of(), command).handle(makeCall(GUILD, Set.of()), reply);

        assertThat(reply.events).containsExactly("Rate limit reached. Try again in 17 s.");
        verifyNoInteractions(identities);
        assertThat(command.calls).isEmpty();
    }

    // AC4

    @Test
    void anAdmittedCommand_isDeferredThenGetsTheResolvedDiscordUser() {
        admit();
        FakeCommand command = new FakeCommand();
        DiscordCall call = makeCall(GUILD, Set.of(ROLE));

        makeHandler(Set.of(ROLE), command).handle(call, reply);

        assertThat(reply.events).containsExactly(RecordingDiscordReply.DEFER, "handled");
        verify(identities).resolve(ExternalPlatform.DISCORD, "300", "Ada");
        verify(rateLimiter).tryAcquire(DISCORD_USER);
        assertThat(command.calls).containsExactly(USER_ID);
        assertThat(command.receivedCall).isEqualTo(call);
    }

    // AC5

    @Test
    void anExceptionFromTheCommand_givesTheFixedReplyAndLogsOnlyItsClass() {
        admit();
        FakeCommand command = new FakeCommand();
        command.failure = new IllegalStateException("secret detail");
        Logger logger = (Logger) LoggerFactory.getLogger(DiscordCommandHandler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            makeHandler(Set.of(), command).handle(makeCall(GUILD, Set.of()), reply);
        } finally {
            logger.detachAppender(logs);
        }

        assertThat(reply.events).containsExactly(RecordingDiscordReply.DEFER, FAILED);
        assertThat(logs.list).isNotEmpty()
            .allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("secret detail");
                assertThat(event.getThrowableProxy()).isNull();
            })
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("IllegalStateException"));
    }

    private void admit() {
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(OptionalLong.empty());
        when(identities.resolve(any(), any(), any())).thenReturn(USER_ID);
    }

    private DiscordCommandHandler makeHandler(Set<Long> roleIds, DiscordCommand command) {
        return new DiscordCommandHandler(new DiscordAllowlist(Set.of(GUILD), roleIds), rateLimiter, identities,
            List.of(command));
    }

    private static DiscordCall makeCall(Long guildId, Set<Long> roleIds) {
        return new DiscordCall(guildId, roleIds, DISCORD_USER, "Ada", "fake", null, Map.of());
    }

    private static final class FakeCommand implements DiscordCommand {

        final List<UUID> calls = new ArrayList<>();
        DiscordCall receivedCall;
        RuntimeException failure;

        @Override
        public SlashCommandData definition() {
            return Commands.slash("fake", "A fake command");
        }

        @Override
        public void handle(DiscordCall call, UUID userId, DiscordReply reply) {
            calls.add(userId);
            receivedCall = call;
            if (failure != null) {
                throw failure;
            }
            reply.send("handled");
        }
    }
}
