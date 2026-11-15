package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.slack.SlackAskCommand;
import dev.mindforge.slack.SlackCall;
import dev.mindforge.slack.SlackKnowledgeBases;

class SlackAskCommandTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String QUESTION = "What is osmosis?";
    private static final String NOT_ONE = "This command needs exactly one knowledge base. "
        + "Selecting a knowledge base is not available in Slack yet.";
    private static final String MODEL_UNAVAILABLE = "The model is not available now. Try again later.";
    private static final String USAGE = "Usage: /mf-ask <question> (at most 4000 characters).";
    private static final String SECRET = "provider said sk-secret";

    private final QueryService queryService = mock(QueryService.class);
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final SlackAskCommand command =
        new SlackAskCommand(queryService, new SlackKnowledgeBases(knowledgeBaseService));
    private final RecordingSlackReply reply = new RecordingSlackReply();

    @Test
    void name_isMfAsk() {
        assertThat(command.name()).isEqualTo("/mf-ask");
    }

    // AC2, AC7

    @Test
    void theOnlyKnowledgeBase_startsAnInteractionAsksOnceAndRepliesWithAnswerAndSources() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION)).thenReturn(
            makeAnswer("Water moves across a membrane.", "concepts/osmosis", "concepts/cell"));

        command.handle(makeCall(QUESTION), USER_ID, reply);

        InOrder order = inOrder(knowledgeBaseService, queryService);
        order.verify(knowledgeBaseService).requireOwner(kb.kbId(), USER_ID);
        order.verify(queryService).start(kb.kbId(), USER_ID);
        order.verify(queryService).ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION);
        assertThat(reply.events).containsExactly(
            "Water moves across a membrane.\n\nSources:\n- concepts/osmosis\n- concepts/cell");
    }

    @Test
    void anAnswerWithoutCitedPaths_hasNoSourcesBlock() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION))
            .thenReturn(makeAnswer("The wiki has no page on that."));

        command.handle(makeCall(QUESTION), USER_ID, reply);

        assertThat(reply.events).containsExactly("The wiki has no page on that.");
    }

    // AC3

    @Test
    void twoAsks_startTwoInteractions() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        Interaction first = makeInteraction(kb.kbId());
        Interaction second = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(first, second);
        when(queryService.ask(any(), any(), any(), any())).thenReturn(makeAnswer("A."));

        command.handle(makeCall(QUESTION), USER_ID, reply);
        command.handle(makeCall(QUESTION), USER_ID, reply);

        verify(queryService, times(2)).start(kb.kbId(), USER_ID);
        verify(queryService).ask(kb.kbId(), USER_ID, first.interactionId(), QUESTION);
        verify(queryService).ask(kb.kbId(), USER_ID, second.interactionId(), QUESTION);
    }

    // AC4

    @Test
    void noOnlyKnowledgeBase_givesTheFixedReplyAndNoQuery() {
        when(knowledgeBaseService.list(USER_ID)).thenReturn(List.of());

        command.handle(makeCall(QUESTION), USER_ID, reply);

        assertThat(reply.events).containsExactly(NOT_ONE);
        verifyNoInteractions(queryService);
    }

    static List<RuntimeException> ownershipFailures() {
        return List.of(new NotOwnerException(UUID.randomUUID()), new NotFoundException(SECRET));
    }

    @ParameterizedTest
    @MethodSource("ownershipFailures")
    void anOwnershipFailureFromTheQuery_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(makeInteraction(kb.kbId()));
        when(queryService.ask(any(), any(), any(), any())).thenThrow(failure);

        command.handle(makeCall(QUESTION), USER_ID, reply);

        assertThat(reply.events).containsExactly(NOT_ONE);
    }

    static List<RuntimeException> modelFailures() {
        return List.of(
            new AIGatewayUnavailableException(SECRET, new IllegalStateException(SECRET)),
            new DeadlineExceededException(DeadlineProfile.values()[0], Duration.ofSeconds(30)),
            new ModelOutputException("AnswerWriter", new IllegalStateException(SECRET)));
    }

    @ParameterizedTest
    @MethodSource("modelFailures")
    void aModelFailure_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(makeInteraction(kb.kbId()));
        when(queryService.ask(any(), any(), any(), any())).thenThrow(failure);

        command.handle(makeCall(QUESTION), USER_ID, reply);

        assertThat(reply.events).containsExactly(MODEL_UNAVAILABLE);
    }

    // AC5

    static List<String> unusableQuestions() {
        return Arrays.asList(null, "", "   ", "x".repeat(4001));
    }

    @ParameterizedTest
    @MethodSource("unusableQuestions")
    void anEmptyOrTooLongQuestion_givesTheUsageReplyAndNoQuery(String text) {
        command.handle(makeCall(text), USER_ID, reply);

        assertThat(reply.events).containsExactly(USAGE);
        verifyNoInteractions(queryService, knowledgeBaseService);
    }

    @Test
    void aQuestionOfExactly4000Characters_isAsked() {
        String question = "x".repeat(4000);
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(kb.kbId(), USER_ID, interaction.interactionId(), question)).thenReturn(makeAnswer("A."));

        command.handle(makeCall(question), USER_ID, reply);

        assertThat(reply.events).containsExactly("A.");
    }

    private KnowledgeBase makeOnlyKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
        when(knowledgeBaseService.list(USER_ID)).thenReturn(List.of(kb));
        return kb;
    }

    private static Interaction makeInteraction(UUID kbId) {
        return new Interaction(UUID.randomUUID(), kbId, USER_ID, Instant.now());
    }

    private static QueryService.Answer makeAnswer(String answer, String... citedPaths) {
        return new QueryService.Answer(answer, List.of(citedPaths));
    }

    private static SlackCall makeCall(String text) {
        return new SlackCall("T0HOME", "T0HOME", "U0ADA", "Ada", "/mf-ask", text);
    }
}
