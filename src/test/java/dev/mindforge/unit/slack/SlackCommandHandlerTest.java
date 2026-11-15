package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.domain.model.ExternalAccounts;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.slack.SlackAllowlist;
import dev.mindforge.slack.SlackCall;
import dev.mindforge.slack.SlackCommand;
import dev.mindforge.slack.SlackCommandHandler;
import dev.mindforge.slack.SlackRateLimiter;
import dev.mindforge.slack.SlackReply;

class SlackCommandHandlerTest {

    private static final String TEAM = "T0HOME";
    private static final String PARTNER_TEAM = "T0PARTNER";
    private static final String FOREIGN_TEAM = "T0FOREIGN";
    private static final String SLACK_USER = "U0ADA";
    private static final String COMMAND = "/mf-fake";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String NOT_AVAILABLE = "This bot is not available here.";
    private static final String FAILED = "Something went wrong. Try again later.";
    private static final String RESOLVED = "<resolved>";
    private static final Executor DIRECT = Runnable::run;

    private final SlackRateLimiter rateLimiter = mock(SlackRateLimiter.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final RecordingSlackReply reply = new RecordingSlackReply();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger handlerLog = (Logger) LoggerFactory.getLogger(SlackCommandHandler.class);

    {
        logs.start();
        handlerLog.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        handlerLog.detachAppender(logs);
    }

    // AC4

    @Test
    void aCallFromAWorkspaceNotOnTheAllowlist_isRejectedBeforeAnythingElse() {
        FakeCommand command = new FakeCommand();

        makeHandler(command, DIRECT).handle(makeCall(FOREIGN_TEAM, FOREIGN_TEAM), reply);

        assertRejectedWithoutWork(command);
    }

    @Test
    void aCallerFromAForeignTeam_isRejectedTheSameWay() {
        FakeCommand command = new FakeCommand();

        makeHandler(command, DIRECT).handle(makeCall(TEAM, FOREIGN_TEAM), reply);

        assertRejectedWithoutWork(command);
    }

    @Test
    void aCallerWithoutATeam_isRejectedTheSameWay() {
        FakeCommand command = new FakeCommand();

        makeHandler(command, DIRECT).handle(makeCall(TEAM, null), reply);

        assertRejectedWithoutWork(command);
    }

    @Test
    void aRejection_logsAReasonCodeAndNoTeamId() {
        makeHandler(new FakeCommand(), DIRECT).handle(makeCall(TEAM, FOREIGN_TEAM), reply);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("reason=team_not_allowed")
                .doesNotContain(TEAM, FOREIGN_TEAM);
        });
        assertThat(reply.events).allSatisfy(text -> assertThat(text).doesNotContain(TEAM, FOREIGN_TEAM));
    }

    // AC5

    @Test
    void aCallOverTheRateLimit_getsTheRetrySecondsAndResolvesNoUser() {
        when(rateLimiter.tryAcquire(SLACK_USER)).thenReturn(OptionalLong.of(17));
        FakeCommand command = new FakeCommand();

        makeHandler(command, DIRECT).handle(makeCall(TEAM, TEAM), reply);

        assertThat(reply.events).containsExactly("Rate limit reached. Try again in 17 s.");
        verifyNoInteractions(identities);
        assertThat(command.calls).isEmpty();
    }

    // AC6

    @Test
    void anAdmittedCall_isAcknowledgedThenResolvedWithTheCallerTeamThenRun() {
        admit();
        FakeCommand command = new FakeCommand();
        SlackCall call = makeCall(TEAM, PARTNER_TEAM);

        makeHandler(command, DIRECT).handle(call, reply);

        assertThat(reply.events).containsExactly(RecordingSlackReply.ACK, RESOLVED, "handled");
        verify(rateLimiter).tryAcquire(SLACK_USER);
        verify(identities).resolve(ExternalPlatform.SLACK,
            ExternalAccounts.slackExternalId(PARTNER_TEAM, SLACK_USER), "ada");
        assertThat(command.calls).containsExactly(USER_ID);
        assertThat(command.receivedCall).isEqualTo(call);
    }

    @Test
    void resolutionAndTheCommand_runOnTheExecutorAfterTheAck() {
        admit();
        FakeCommand command = new FakeCommand();
        List<Runnable> queued = new ArrayList<>();

        makeHandler(command, queued::add).handle(makeCall(TEAM, TEAM), reply);

        assertThat(reply.events).containsExactly(RecordingSlackReply.ACK);
        verifyNoInteractions(identities);
        assertThat(queued).hasSize(1);

        queued.getFirst().run();

        assertThat(reply.events).containsExactly(RecordingSlackReply.ACK, RESOLVED, "handled");
    }

    @Test
    void aFirstContact_isProvisionedThroughTheIdentityService() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(OptionalLong.empty());
        ExternalIdentityRepository identityRepository = mock(ExternalIdentityRepository.class);
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        String externalId = ExternalAccounts.slackExternalId(TEAM, SLACK_USER);
        when(identityRepository.findUserId(ExternalPlatform.SLACK, externalId)).thenReturn(Optional.empty());
        when(identityRepository.createUserAndLink(eq(ExternalPlatform.SLACK), eq(externalId), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));
        FakeCommand command = new FakeCommand();
        SlackCommandHandler handler = new SlackCommandHandler(makeAllowlist(), rateLimiter,
            new ExternalIdentityService(identityRepository, knowledgeBases, TransactionOperations.withoutTransaction()),
            List.of(command), DIRECT);

        handler.handle(makeCall(TEAM, TEAM), reply);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(identityRepository).createUserAndLink(eq(ExternalPlatform.SLACK), eq(externalId), user.capture());
        verify(knowledgeBases).insert(any(KnowledgeBase.class));
        assertThat(command.calls).containsExactly(user.getValue().userId());
    }

    // AC7

    @Test
    void theSameSlackUserFromTwoAllowedTeams_resolvesWithTwoExternalIds() {
        admit();
        SlackCommandHandler handler = makeHandler(new FakeCommand(), DIRECT);

        handler.handle(makeCall(TEAM, TEAM), reply);
        handler.handle(makeCall(TEAM, PARTNER_TEAM), reply);

        ArgumentCaptor<String> externalIds = ArgumentCaptor.forClass(String.class);
        verify(identities, times(2))
            .resolve(eq(ExternalPlatform.SLACK), externalIds.capture(), any());
        assertThat(externalIds.getAllValues())
            .containsExactly(TEAM + "-" + SLACK_USER, PARTNER_TEAM + "-" + SLACK_USER)
            .doesNotHaveDuplicates();
    }

    // AC8

    @Test
    void anUnknownCommand_getsTheFixedReplyAndResolvesNoUser() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(OptionalLong.empty());

        makeHandler(new FakeCommand(), DIRECT)
            .handle(new SlackCall(TEAM, TEAM, SLACK_USER, "ada", "/mf-other", ""), reply);

        assertThat(reply.events).containsExactly(FAILED);
        verifyNoInteractions(identities);
    }

    @Test
    void anExceptionFromResolution_givesTheFixedReplyAndLogsOnlyItsClass() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(OptionalLong.empty());
        when(identities.resolve(any(), any(), any())).thenThrow(new IllegalStateException("secret detail"));
        FakeCommand command = new FakeCommand();

        makeHandler(command, DIRECT).handle(makeCall(TEAM, TEAM), reply);

        assertThat(reply.events).containsExactly(RecordingSlackReply.ACK, FAILED);
        assertThat(command.calls).isEmpty();
        assertLogsOnlyTheClass();
    }

    @Test
    void anExceptionFromTheCommand_givesTheFixedReplyAndLogsOnlyItsClass() {
        admit();
        FakeCommand command = new FakeCommand();
        command.failure = new IllegalStateException("secret detail");

        makeHandler(command, DIRECT).handle(makeCall(TEAM, TEAM), reply);

        assertThat(reply.events).containsExactly(RecordingSlackReply.ACK, RESOLVED, FAILED);
        assertLogsOnlyTheClass();
    }

    private void assertRejectedWithoutWork(FakeCommand command) {
        assertThat(reply.events).containsExactly(NOT_AVAILABLE);
        verifyNoInteractions(rateLimiter, identities);
        assertThat(command.calls).isEmpty();
    }

    private void assertLogsOnlyTheClass() {
        assertThat(reply.events).allSatisfy(text -> assertThat(text).doesNotContain("secret detail"));
        assertThat(logs.list).isNotEmpty()
            .allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("secret detail");
                assertThat(event.getThrowableProxy()).isNull();
            })
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("IllegalStateException"));
    }

    private void admit() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(OptionalLong.empty());
        when(identities.resolve(any(), any(), any())).thenAnswer(invocation -> {
            reply.events.add(RESOLVED);
            return USER_ID;
        });
    }

    private SlackCommandHandler makeHandler(SlackCommand command, Executor executor) {
        return new SlackCommandHandler(makeAllowlist(), rateLimiter, identities, List.of(command), executor);
    }

    private static SlackAllowlist makeAllowlist() {
        return new SlackAllowlist(Set.of(TEAM, PARTNER_TEAM));
    }

    private static SlackCall makeCall(String workspaceTeamId, String callerTeamId) {
        return new SlackCall(workspaceTeamId, callerTeamId, SLACK_USER, "ada", COMMAND, "some text");
    }

    private static final class FakeCommand implements SlackCommand {

        final List<UUID> calls = new ArrayList<>();
        SlackCall receivedCall;
        RuntimeException failure;

        @Override
        public String name() {
            return COMMAND;
        }

        @Override
        public void handle(SlackCall call, UUID userId, SlackReply reply) {
            calls.add(userId);
            receivedCall = call;
            if (failure != null) {
                throw failure;
            }
            reply.send("handled");
        }
    }
}
