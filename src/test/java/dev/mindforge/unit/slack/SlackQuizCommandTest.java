package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.QuizService.Graded;
import dev.mindforge.application.service.QuizService.NextQuestion;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.QuizEvaluation;
import dev.mindforge.domain.model.QuizFinishedException;
import dev.mindforge.domain.model.QuizQuestion;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.slack.SlackCall;
import dev.mindforge.slack.SlackKnowledgeBases;
import dev.mindforge.slack.SlackQuizCommand;
import dev.mindforge.slack.SlackQuizSessions;
import dev.mindforge.slack.SlackQuizSessions.ActiveQuiz;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;

class SlackQuizCommandTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ANSWER = "Cells split.";
    private static final String USAGE = "Usage: /mf-quiz start, or /mf-quiz answer <text> (at most 4000 characters).";
    private static final String NO_ACTIVE_QUIZ = "No active quiz. Run /mf-quiz start.";
    private static final String NOTHING_TO_ASK = "This knowledge base has nothing to quiz on yet.";
    private static final String NOT_ONE = "This command needs exactly one knowledge base. "
        + "Selecting a knowledge base is not available in Slack yet.";
    private static final String MODEL_UNAVAILABLE = "The model is not available now. Try again later.";
    private static final String REFERENCE_ANSWER = "Reference: mitosis is nuclear division into two identical nuclei.";
    private static final String GROUNDING = "Grounding excerpt.";
    private static final String SECRET = "provider said sk-secret";

    private final QuizService quizService = mock(QuizService.class);
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final SlackKnowledgeBases knowledgeBases = new SlackKnowledgeBases(knowledgeBaseService);
    private final SlackQuizSessions quizzes = new SlackQuizSessions(Duration.ofHours(2));
    private final SlackQuizCommand command = new SlackQuizCommand(quizService, knowledgeBases, quizzes);
    private final RecordingSlackReply reply = new RecordingSlackReply();

    @Test
    void name_isMfQuiz() {
        assertThat(command.name()).isEqualTo("/mf-quiz");
    }

    // AC2

    @ParameterizedTest
    @ValueSource(strings = {"start", "START", "  Start  "})
    void start_onTheOnlyKnowledgeBase_quizzesTheWholeKbStoresTheEntryAndRepliesWithTheFirstQuestion(String text) {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        UUID sessionId = givenStartedSession(kb.kbId());
        when(quizService.next(kb.kbId(), USER_ID, sessionId))
            .thenReturn(Optional.of(makeNextQuestion(1, "What is mitosis?")));

        command.handle(makeCall(text), USER_ID, reply);

        verify(quizService).start(kb.kbId(), USER_ID, new StudyScope.WholeKnowledgeBase());
        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kb.kbId(), sessionId));
        assertThat(reply.events).containsExactly("Question 1/5: What is mitosis?");
    }

    @Test
    void start_withoutAnOnlyKnowledgeBase_givesTheFixedReplyAndStartsNothing() {
        when(knowledgeBaseService.list(USER_ID)).thenReturn(List.of());

        command.handle(makeCall("start"), USER_ID, reply);

        assertThat(reply.events).containsExactly(NOT_ONE);
        verifyNoInteractions(quizService);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    @Test
    void start_withNothingToQuizOn_givesTheFixedReplyWithoutTheMessage() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        when(quizService.start(eq(kb.kbId()), eq(USER_ID), any())).thenThrow(new NotFoundException(SECRET));

        command.handle(makeCall("start"), USER_ID, reply);

        assertThat(reply.events).containsExactly(NOTHING_TO_ASK);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    @Test
    void aSecondStart_replacesTheStoredEntry() {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(quizService.start(eq(kb.kbId()), eq(USER_ID), any()))
            .thenReturn(makeSession(kb.kbId(), first), makeSession(kb.kbId(), second));
        when(quizService.next(any(), any(), any())).thenReturn(Optional.of(makeNextQuestion(1, "Q?")));

        command.handle(makeCall("start"), USER_ID, reply);
        command.handle(makeCall("start"), USER_ID, reply);

        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kb.kbId(), second));
    }

    // AC3

    @Test
    void answer_withAnEntry_gradesItAndRepliesWithScoreFeedbackAndTheNextQuestion() {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER)).thenReturn(makeGraded(4, "Nearly right.", false));
        when(quizService.next(kbId, USER_ID, sessionId))
            .thenReturn(Optional.of(makeNextQuestion(2, "What is meiosis?")));

        command.handle(makeCall("Answer  " + ANSWER + " "), USER_ID, reply);

        assertThat(reply.events).containsExactly("Score: 4/5\nNearly right.\n\nQuestion 2/5: What is meiosis?");
        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kbId, sessionId));
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void answer_toTheLastQuestion_repliesWithScoreAndFeedbackAndClearsTheEntry() {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER)).thenReturn(makeGraded(5, "Correct.", true));

        command.handle(makeCall("answer " + ANSWER), USER_ID, reply);

        assertThat(reply.events).containsExactly("Score: 5/5\nCorrect.\n\nQuiz finished.");
        assertThat(quizzes.current(USER_ID)).isEmpty();
        verify(quizService, never()).next(any(), any(), any());
    }

    // AC4

    @Test
    void answer_withNoEntry_givesTheHintAndCallsNoService() {
        command.handle(makeCall("answer " + ANSWER), USER_ID, reply);

        assertThat(reply.events).containsExactly(NO_ACTIVE_QUIZ);
        verifyNoInteractions(quizService);
    }

    static List<String> unusableAnswers() {
        return List.of("answer", "answer   ", "answer " + "x".repeat(4001));
    }

    @ParameterizedTest
    @MethodSource("unusableAnswers")
    void answer_withEmptyOrTooLongText_givesTheUsageLineAndCallsNoService(String text) {
        quizzes.put(USER_ID, new ActiveQuiz(UUID.randomUUID(), UUID.randomUUID()));

        command.handle(makeCall(text), USER_ID, reply);

        assertThat(reply.events).containsExactly(USAGE);
        verifyNoInteractions(quizService);
    }

    static List<String> unusableVerbs() {
        return Arrays.asList(null, "", "   ", "begin", "starts now", "help");
    }

    @ParameterizedTest
    @MethodSource("unusableVerbs")
    void anEmptyOrUnknownVerb_givesTheUsageLineAndNeverStartsAQuiz(String text) {
        command.handle(makeCall(text), USER_ID, reply);

        assertThat(reply.events).containsExactly(USAGE);
        verifyNoInteractions(quizService, knowledgeBaseService);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    // AC5

    @Test
    void answer_whoseEntryPointsAtAnotherUsersSession_givesTheNoQuizReplyThroughTheRealOwnershipCheck() {
        UUID kbId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        QuizSession sessionOfB = makeSessionWithReference(kbId, userB);
        QuizSessionStore store = mock(QuizSessionStore.class);
        when(store.find(eq(kbId), eq(sessionOfB.sessionId()), any())).thenReturn(Optional.of(sessionOfB));
        StubAIGateway gateway = new StubAIGateway();
        SlackQuizCommand realCommand = new SlackQuizCommand(makeRealService(store, gateway), knowledgeBases, quizzes);
        quizzes.put(userA, new ActiveQuiz(kbId, sessionOfB.sessionId()));

        realCommand.handle(makeCall("answer " + ANSWER), userA, reply);

        assertThat(reply.events).containsExactly(NO_ACTIVE_QUIZ);
        assertThat(quizzes.current(userA)).isEmpty();
        assertThat(gateway.recordedCalls()).isEmpty();
        verify(store, never()).updateCursor(any(), any(), any(Integer.class));
    }

    static List<RuntimeException> sessionGone() {
        return List.of(new NotFoundException(SECRET), new NotOwnerException(UUID.randomUUID()),
            new QuizFinishedException(UUID.randomUUID()));
    }

    @ParameterizedTest
    @MethodSource("sessionGone")
    void answer_toAMissingForeignOrFinishedSession_clearsTheEntryAndGivesTheNoQuizReply(RuntimeException failure) {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER)).thenThrow(failure);

        command.handle(makeCall("answer " + ANSWER), USER_ID, reply);

        assertThat(reply.events).containsExactly(NO_ACTIVE_QUIZ);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    // AC6

    static List<RuntimeException> modelFailures() {
        return List.of(
            new AIGatewayUnavailableException(SECRET, new IllegalStateException(SECRET)),
            new DeadlineExceededException(DeadlineProfile.values()[0], Duration.ofSeconds(30)),
            new ModelOutputException("QuizGenerator", new IllegalStateException(SECRET)));
    }

    @ParameterizedTest
    @MethodSource("modelFailures")
    void aModelFailureOnStart_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeOnlyKnowledgeBase();
        when(quizService.start(eq(kb.kbId()), eq(USER_ID), any())).thenThrow(failure);

        command.handle(makeCall("start"), USER_ID, reply);

        assertThat(reply.events).containsExactly(MODEL_UNAVAILABLE);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("modelFailures")
    void aModelFailureOnAnswer_givesTheFixedReplyAndKeepsTheEntry(RuntimeException failure) {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER)).thenThrow(failure);

        command.handle(makeCall("answer " + ANSWER), USER_ID, reply);

        assertThat(reply.events).containsExactly(MODEL_UNAVAILABLE);
        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kbId, sessionId));
    }

    // AC7

    @Test
    void answer_throughTheRealService_repliesWithoutTheReferenceAnswerOrGroundingContext() {
        UUID kbId = UUID.randomUUID();
        QuizSession session = makeSessionWithReference(kbId, USER_ID);
        QuizSessionStore store = mock(QuizSessionStore.class);
        when(store.find(eq(kbId), eq(session.sessionId()), any())).thenReturn(Optional.of(session),
            Optional.of(session.advanced()));
        StubAIGateway gateway = new StubAIGateway().answer(Prompts.GRADE, "{\"score\": 3, \"feedback\": \"Partly.\"}");
        SlackQuizCommand realCommand = new SlackQuizCommand(makeRealService(store, gateway), knowledgeBases, quizzes);
        quizzes.put(USER_ID, new ActiveQuiz(kbId, session.sessionId()));

        realCommand.handle(makeCall("answer " + ANSWER), USER_ID, reply);

        assertThat(reply.events).containsExactly("Score: 3/5\nPartly.\n\nQuestion 2/2: What is meiosis?");
        assertThat(String.join("\n", reply.events)).doesNotContain(REFERENCE_ANSWER, GROUNDING);
    }

    private UUID givenStartedSession(UUID kbId) {
        UUID sessionId = UUID.randomUUID();
        when(quizService.start(eq(kbId), eq(USER_ID), any())).thenReturn(makeSession(kbId, sessionId));
        return sessionId;
    }

    private KnowledgeBase makeOnlyKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
        when(knowledgeBaseService.list(USER_ID)).thenReturn(List.of(kb));
        return kb;
    }

    private static QuizService makeRealService(QuizSessionStore store, StubAIGateway gateway) {
        PromptLoader prompts = new PromptLoader("en");
        return new QuizService(mock(WikiStore.class), mock(StudyProgressStore.class), store,
            new QuizGenerator(gateway, prompts), new QuizEvaluator(gateway, prompts), ProcessingSettings.defaults(),
            Duration.ofHours(1));
    }

    private static NextQuestion makeNextQuestion(int index, String question) {
        return new NextQuestion(index, 5, question);
    }

    private static Graded makeGraded(int score, String feedback, boolean finished) {
        return new Graded(new QuizEvaluation(score, feedback), finished);
    }

    private static QuizSession makeSession(UUID kbId, UUID sessionId) {
        return new QuizSession(sessionId, kbId, USER_ID, List.of(new QuizQuestion(UUID.randomUUID(), null,
            "What is mitosis?", REFERENCE_ANSWER, GROUNDING)), 0, Instant.now().plusSeconds(3600));
    }

    private static QuizSession makeSessionWithReference(UUID kbId, UUID owner) {
        return new QuizSession(UUID.randomUUID(), kbId, owner, List.of(
            new QuizQuestion(UUID.randomUUID(), null, "What is mitosis?", REFERENCE_ANSWER, GROUNDING),
            new QuizQuestion(UUID.randomUUID(), null, "What is meiosis?", REFERENCE_ANSWER, GROUNDING)),
            0, Instant.now().plusSeconds(3600));
    }

    private static SlackCall makeCall(String text) {
        return new SlackCall("T0HOME", "T0HOME", "U0ADA", "Ada", "/mf-quiz", text);
    }
}
