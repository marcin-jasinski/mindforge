package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.QuizService.Graded;
import dev.mindforge.application.service.QuizService.NextQuestion;
import dev.mindforge.discord.DiscordAllowlist;
import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordCommandHandler;
import dev.mindforge.discord.DiscordKnowledgeBases;
import dev.mindforge.discord.DiscordQuizSessions;
import dev.mindforge.discord.DiscordQuizSessions.ActiveQuiz;
import dev.mindforge.discord.DiscordRateLimiter;
import dev.mindforge.discord.QuizCommand;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.QuizEvaluation;
import dev.mindforge.domain.model.QuizQuestion;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

class QuizCommandTest {

    private static final long GUILD = 100L;
    private static final long DISCORD_USER = 300L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ANSWER = "Cells split.";
    private static final String NO_ACTIVE_QUIZ = "No active quiz. Run /quiz start.";
    private static final String NOTHING_TO_ASK = "This knowledge base has nothing to quiz on yet.";
    private static final String MODEL_UNAVAILABLE = "The model is not available now. Try again later.";
    private static final String UNKNOWN = "Unknown knowledge base.";
    private static final String REFERENCE_ANSWER = "Reference: mitosis is nuclear division into two identical nuclei.";
    private static final String SECRET = "provider said sk-secret";

    private final QuizService quizService = mock(QuizService.class);
    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final DiscordKnowledgeBases knowledgeBases = new DiscordKnowledgeBases(
        new DiscordAllowlist(Set.of(GUILD), Set.of()), identities,
            new KnowledgeBaseService(repository, mock(QuizSessionStore.class)));
    private final DiscordQuizSessions quizzes = new DiscordQuizSessions(Duration.ofHours(2));
    private final QuizCommand command = new QuizCommand(quizService, knowledgeBases, quizzes);
    private final RecordingDiscordReply reply = new RecordingDiscordReply();

    // AC1

    @Test
    void definition_hasAStartSubcommandWithKbAndAnAnswerSubcommandWithText() {
        assertThat(command.definition().getName()).isEqualTo("quiz");
        assertThat(command.definition().getSubcommands()).extracting(SubcommandData::getName)
            .containsExactly("start", "answer");
        assertThat(command.definition().getSubcommands().get(0).getOptions())
            .extracting(OptionData::getName, OptionData::getType, OptionData::isRequired, OptionData::isAutoComplete)
            .containsExactly(tuple("kb", OptionType.STRING, true, true));
        assertThat(command.definition().getSubcommands().get(1).getOptions())
            .extracting(OptionData::getName, OptionData::getType, OptionData::isRequired, OptionData::isAutoComplete)
            .containsExactly(tuple("text", OptionType.STRING, true, false));
    }

    // AC2

    @Test
    void start_onAnOwnedKnowledgeBase_quizzesTheWholeKbStoresTheEntryAndRepliesWithTheFirstQuestion() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        UUID sessionId = givenStartedSession(kb.kbId());
        when(quizService.next(kb.kbId(), USER_ID, sessionId))
            .thenReturn(Optional.of(new NextQuestion(1, 5, "What is mitosis?")));

        command.handle(makeStart(kb.kbId().toString()), USER_ID, reply);

        verify(quizService).start(kb.kbId(), USER_ID, new StudyScope.WholeKnowledgeBase());
        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kb.kbId(), sessionId));
        assertThat(reply.events).containsExactly("Question 1/5: What is mitosis?");
    }

    @Test
    void start_onAnotherUsersKnowledgeBase_givesTheFixedReplyAndStartsNothing() {
        KnowledgeBase foreign = new KnowledgeBase(UUID.randomUUID(), UUID.randomUUID(), "Theirs", null, null, 0, 0);
        when(repository.findById(foreign.kbId())).thenReturn(Optional.of(foreign));

        command.handle(makeStart(foreign.kbId().toString()), USER_ID, reply);

        assertThat(reply.events).containsExactly(UNKNOWN);
        verifyNoInteractions(quizService);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    // AC3

    @Test
    void aSecondStart_replacesTheStoredEntry() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(quizService.start(eq(kb.kbId()), eq(USER_ID), any()))
            .thenReturn(makeSession(kb.kbId(), first), makeSession(kb.kbId(), second));
        when(quizService.next(any(), any(), any())).thenReturn(Optional.of(new NextQuestion(1, 5, "Q?")));

        command.handle(makeStart(kb.kbId().toString()), USER_ID, reply);
        command.handle(makeStart(kb.kbId().toString()), USER_ID, reply);

        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kb.kbId(), second));
    }

    // AC4

    @Test
    void answer_withAnEntry_gradesItAndRepliesWithScoreFeedbackAndTheNextQuestion() {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER))
            .thenReturn(new Graded(new QuizEvaluation(4, "Nearly right."), false));
        when(quizService.next(kbId, USER_ID, sessionId))
            .thenReturn(Optional.of(new NextQuestion(2, 5, "What is meiosis?")));

        command.handle(makeAnswer(), USER_ID, reply);

        assertThat(reply.events).containsExactly("Score: 4/5\nNearly right.\n\nQuestion 2/5: What is meiosis?");
        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kbId, sessionId));
    }

    @Test
    void answer_toTheLastQuestion_repliesWithScoreAndFeedbackAndClearsTheEntry() {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER))
            .thenReturn(new Graded(new QuizEvaluation(5, "Correct."), true));

        command.handle(makeAnswer(), USER_ID, reply);

        assertThat(reply.events).containsExactly("Score: 5/5\nCorrect.\n\nQuiz finished.");
        assertThat(quizzes.current(USER_ID)).isEmpty();
        verify(quizService, never()).next(any(), any(), any());
    }

    // AC5

    @Test
    void answer_withNoEntry_givesTheFixedReplyAndCallsNoService() {
        command.handle(makeAnswer(), USER_ID, reply);

        assertThat(reply.events).containsExactly(NO_ACTIVE_QUIZ);
        verifyNoInteractions(quizService);
    }

    // AC6 (18.4)

    @Test
    void answer_whoseEntryPointsAtAnotherUsersSession_givesTheNoQuizReplyThroughTheRealOwnershipCheck() {
        UUID kbId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        QuizSession sessionOfA = makeSessionWithReference(kbId, userA);
        QuizSessionStore store = mock(QuizSessionStore.class);
        when(store.find(eq(kbId), eq(sessionOfA.sessionId()), any())).thenReturn(Optional.of(sessionOfA));
        StubAIGateway gateway = new StubAIGateway();
        QuizCommand realCommand = new QuizCommand(makeRealService(store, gateway), knowledgeBases, quizzes);
        quizzes.put(userB, new ActiveQuiz(kbId, sessionOfA.sessionId()));

        realCommand.handle(makeAnswer(), userB, reply);

        assertThat(reply.events).containsExactly(NO_ACTIVE_QUIZ);
        assertThat(quizzes.current(userB)).isEmpty();
        assertThat(gateway.recordedCalls()).isEmpty();
        verify(store, never()).updateCursor(any(), any(), any(Integer.class));
    }

    @Test
    void answer_toAnExpiredOrMissingSession_clearsTheEntryAndGivesTheNoQuizReply() {
        UUID kbId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        quizzes.put(USER_ID, new ActiveQuiz(kbId, sessionId));
        when(quizService.answer(kbId, USER_ID, sessionId, ANSWER)).thenThrow(new NotFoundException(SECRET));

        command.handle(makeAnswer(), USER_ID, reply);

        assertThat(reply.events).containsExactly(NO_ACTIVE_QUIZ);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    // AC7

    @Test
    void answer_throughTheRealService_repliesWithoutTheReferenceAnswer() {
        UUID kbId = UUID.randomUUID();
        QuizSession session = makeSessionWithReference(kbId, USER_ID);
        QuizSessionStore store = mock(QuizSessionStore.class);
        when(store.find(eq(kbId), eq(session.sessionId()), any())).thenReturn(Optional.of(session),
            Optional.of(session.advanced()));
        StubAIGateway gateway = new StubAIGateway().answer(Prompts.GRADE, "{\"score\": 3, \"feedback\": \"Partly.\"}");
        QuizCommand realCommand = new QuizCommand(makeRealService(store, gateway), knowledgeBases, quizzes);
        quizzes.put(USER_ID, new ActiveQuiz(kbId, session.sessionId()));

        realCommand.handle(makeAnswer(), USER_ID, reply);

        assertThat(reply.events).containsExactly("Score: 3/5\nPartly.\n\nQuestion 2/2: What is meiosis?");
        assertThat(String.join("\n", reply.events)).doesNotContain(REFERENCE_ANSWER, "Grounding");
    }

    @Test
    void start_withNothingToAskAbout_givesTheFixedReplyWithoutTheMessage() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        when(quizService.start(eq(kb.kbId()), eq(USER_ID), any())).thenThrow(new NotFoundException(SECRET));

        command.handle(makeStart(kb.kbId().toString()), USER_ID, reply);

        assertThat(reply.events).containsExactly(NOTHING_TO_ASK);
        assertThat(quizzes.current(USER_ID)).isEmpty();
    }

    static List<RuntimeException> modelFailures() {
        return List.of(
            new AIGatewayUnavailableException(SECRET, new IllegalStateException(SECRET)),
            new DeadlineExceededException(DeadlineProfile.values()[0], Duration.ofSeconds(30)),
            new ModelOutputException("QuizGenerator", new IllegalStateException(SECRET)));
    }

    @ParameterizedTest
    @MethodSource("modelFailures")
    void aModelFailureOnStart_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        when(quizService.start(eq(kb.kbId()), eq(USER_ID), any())).thenThrow(failure);

        command.handle(makeStart(kb.kbId().toString()), USER_ID, reply);

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

        command.handle(makeAnswer(), USER_ID, reply);

        assertThat(reply.events).containsExactly(MODEL_UNAVAILABLE);
        assertThat(quizzes.current(USER_ID)).contains(new ActiveQuiz(kbId, sessionId));
    }

    // AC8

    @Test
    void throughTheGate_bothSubcommandsAreDeferredEphemerallyAndNeverUseBeginOrIsPreparing() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        UUID sessionId = givenStartedSession(kb.kbId());
        when(quizService.next(kb.kbId(), USER_ID, sessionId))
            .thenReturn(Optional.of(new NextQuestion(1, 5, "What is mitosis?")));
        when(quizService.answer(kb.kbId(), USER_ID, sessionId, ANSWER))
            .thenReturn(new Graded(new QuizEvaluation(5, "Correct."), true));
        DiscordRateLimiter rateLimiter = mock(DiscordRateLimiter.class);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(OptionalLong.empty());
        when(identities.resolve(ExternalPlatform.DISCORD, "300", "Ada")).thenReturn(USER_ID);
        DiscordCommandHandler handler = new DiscordCommandHandler(new DiscordAllowlist(Set.of(GUILD), Set.of()),
            rateLimiter, identities, List.of(command));
        RecordingDiscordReply answerReply = new RecordingDiscordReply();

        handler.handle(makeStart(kb.kbId().toString()), reply);
        handler.handle(makeAnswer(), answerReply);

        assertThat(reply.events).containsExactly(RecordingDiscordReply.DEFER, "Question 1/5: What is mitosis?");
        assertThat(answerReply.events)
            .containsExactly(RecordingDiscordReply.DEFER, "Score: 5/5\nCorrect.\n\nQuiz finished.");
        verify(quizService, never()).begin(any(), any(), any());
        verify(quizService, never()).isPreparing(any(), any(), any());
    }

    private UUID givenStartedSession(UUID kbId) {
        UUID sessionId = UUID.randomUUID();
        when(quizService.start(eq(kbId), eq(USER_ID), any())).thenReturn(makeSession(kbId, sessionId));
        return sessionId;
    }

    private KnowledgeBase makeOwnedKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
        when(repository.findById(kb.kbId())).thenReturn(Optional.of(kb));
        return kb;
    }

    private static QuizService makeRealService(QuizSessionStore store, StubAIGateway gateway) {
        PromptLoader prompts = new PromptLoader("en");
        return new QuizService(mock(WikiStore.class), mock(StudyProgressStore.class), store,
            new QuizGenerator(gateway, prompts), new QuizEvaluator(gateway, prompts), ProcessingSettings.defaults(),
            Duration.ofHours(1));
    }

    private static QuizSession makeSession(UUID kbId, UUID sessionId) {
        return new QuizSession(sessionId, kbId, USER_ID, List.of(new QuizQuestion(UUID.randomUUID(), null,
            "What is mitosis?", REFERENCE_ANSWER, "Grounding excerpt.")), 0, Instant.now().plusSeconds(3600));
    }

    private static QuizSession makeSessionWithReference(UUID kbId, UUID owner) {
        return new QuizSession(UUID.randomUUID(), kbId, owner, List.of(
            new QuizQuestion(UUID.randomUUID(), null, "What is mitosis?", REFERENCE_ANSWER, "Grounding excerpt."),
            new QuizQuestion(UUID.randomUUID(), null, "What is meiosis?", REFERENCE_ANSWER, "Grounding excerpt.")),
            0, Instant.now().plusSeconds(3600));
    }

    private static DiscordCall makeStart(String kbOption) {
        return new DiscordCall(GUILD, Set.of(), DISCORD_USER, "Ada", "quiz", "start", Map.of("kb", kbOption));
    }

    private static DiscordCall makeAnswer() {
        return new DiscordCall(GUILD, Set.of(), DISCORD_USER, "Ada", "quiz", "answer", Map.of("text", ANSWER));
    }
}
