package dev.mindforge.unit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.QuizService.Graded;
import dev.mindforge.application.service.QuizService.NextQuestion;
import dev.mindforge.cli.QuizCommandRunner;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.QuizEvaluation;
import dev.mindforge.domain.model.QuizQuestion;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.User;

class QuizCommandRunnerTest {

    private static final String EMAIL = "ada@example.com";
    private static final UUID KB_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String REFERENCE_ANSWER = "SECRET-REFERENCE-ANSWER";
    private static final String GROUNDING_EXCERPT = "SECRET-GROUNDING-EXCERPT";

    private final AccountService accounts = mock(AccountService.class);
    private final KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);
    private final QuizService quizzes = mock(QuizService.class);
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void anotherCommandDoesNothing() {
        QuizCommandRunner runner = makeRunner("");

        runner.run(new DefaultApplicationArguments("pipeline", "--kb=" + KB_ID, "--user=" + EMAIL));

        assertThat(runner.getExitCode()).isZero();
        assertThat(out.size()).isZero();
        assertThat(err.size()).isZero();
        verifyNoInteractions(accounts, knowledgeBases, quizzes);
    }

    @Test
    void aMissingOrInvalidOptionPrintsTheUsageAndCallsNoService() {
        List<String[]> cases = List.of(
            new String[] {"quiz", "--user=" + EMAIL},
            new String[] {"quiz", "--kb=" + KB_ID},
            new String[] {"quiz", "--kb=not-a-uuid", "--user=" + EMAIL});
        for (String[] args : cases) {
            ByteArrayOutputStream caseErr = new ByteArrayOutputStream();
            QuizCommandRunner caseRunner = new QuizCommandRunner(accounts, knowledgeBases, quizzes,
                new ByteArrayInputStream(new byte[0]), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(caseErr, true, StandardCharsets.UTF_8));

            caseRunner.run(new DefaultApplicationArguments(args));

            assertThat(caseErr.toString(StandardCharsets.UTF_8)).as(String.join(" ", args)).startsWith("Usage:");
            assertThat(caseRunner.getExitCode()).as(String.join(" ", args)).isEqualTo(2);
        }
        verifyNoInteractions(accounts, knowledgeBases, quizzes);
    }

    @Test
    void anUnknownUserIsRefusedBeforeTheOwnershipCheck() {
        when(accounts.byEmail(EMAIL)).thenThrow(new NotFoundException("User"));
        QuizCommandRunner runner = makeRunner("");

        runner.run(makeArgs());

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).containsExactly("user not found: " + EMAIL);
        verifyNoInteractions(knowledgeBases, quizzes);
    }

    @Test
    void aKnowledgeBaseNotFoundOrNotOwnedIsRefusedBeforeAnyQuizStarts() {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        List<RuntimeException> refusals = List.of(new NotOwnerException(KB_ID),
            new NotFoundException("Knowledge base"));
        for (RuntimeException refusal : refusals) {
            ByteArrayOutputStream caseErr = new ByteArrayOutputStream();
            QuizCommandRunner caseRunner = new QuizCommandRunner(accounts, knowledgeBases, quizzes,
                new ByteArrayInputStream(new byte[0]), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(caseErr, true, StandardCharsets.UTF_8));
            doThrow(refusal).when(knowledgeBases).requireOwner(KB_ID, USER_ID);

            caseRunner.run(makeArgs());

            assertThat(caseRunner.getExitCode()).isEqualTo(2);
            assertThat(caseErr.toString(StandardCharsets.UTF_8).lines().toList())
                .containsExactly("knowledge base not found or not owned by " + EMAIL);
        }
        verifyNoInteractions(quizzes);
    }

    @Test
    void theQuizRunsOverTheWholeKnowledgeBaseAsTheResolvedUser() {
        givenAQuizOf(2);
        QuizCommandRunner runner = makeRunner("Division.\nGametes.\n");

        runner.run(makeArgs());

        verify(quizzes).start(KB_ID, USER_ID, new StudyScope.WholeKnowledgeBase());
        verify(quizzes, times(2)).next(KB_ID, USER_ID, SESSION_ID);
        verify(quizzes).answer(KB_ID, USER_ID, SESSION_ID, "Division.");
        verify(quizzes).answer(KB_ID, USER_ID, SESSION_ID, "Gametes.");
        verifyNoMoreInteractions(quizzes);
    }

    @Test
    void fiveAnswersPrintEachQuestionScoreAndFeedbackAndASummary() {
        givenAQuizOf(5);
        QuizCommandRunner runner = makeRunner("a1\na2\na3\na4\na5\n");

        runner.run(makeArgs());

        assertThat(runner.getExitCode()).isZero();
        String stdout = out.toString(StandardCharsets.UTF_8);
        for (int i = 1; i <= 5; i++) {
            assertThat(stdout).contains("Question " + i + "/5: Question " + i + "?")
                .contains("Score: " + (i - 1) + "/5")
                .contains("Feedback " + i + ".");
        }
        assertThat(stdout.lines().toList().getLast())
            .isEqualTo("Quiz ended: 5 of 5 questions answered, total score 10, mean score 2.0/5");
        verify(quizzes, times(5)).answer(eq(KB_ID), eq(USER_ID), eq(SESSION_ID), anyString());
        assertThat(err.size()).isZero();
    }

    @Test
    void endOfInputAfterTheFirstQuestionEndsTheQuizWithTheSummary() {
        givenAQuizOf(5);
        QuizCommandRunner runner = makeRunner("only one\n");

        runner.run(makeArgs());

        assertThat(runner.getExitCode()).isZero();
        verify(quizzes, times(1)).answer(eq(KB_ID), eq(USER_ID), eq(SESSION_ID), anyString());
        assertThat(out.toString(StandardCharsets.UTF_8).lines().toList().getLast())
            .isEqualTo("Quiz ended: 1 of 5 questions answered, total score 0, mean score 0.0/5");
        assertThat(err.size()).isZero();
    }

    @Test
    void nothingToAskIsRefusedWithOneStderrLineAndNoQuestion() {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        when(quizzes.start(KB_ID, USER_ID, new StudyScope.WholeKnowledgeBase()))
            .thenThrow(new NotFoundException("Pages to study"));
        QuizCommandRunner runner = makeRunner("a1\n");

        runner.run(makeArgs());

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).singleElement().asString().contains("Pages to study not found");
        verify(quizzes, never()).next(any(), any(), any());
        verify(quizzes, never()).answer(any(), any(), any(), any());
    }

    @Test
    void theConsoleNeverShowsAReferenceAnswerOrAGroundingExcerpt() {
        givenAQuizOf(5);
        QuizCommandRunner runner = makeRunner("a1\na2\na3\na4\na5\n");

        runner.run(makeArgs());

        for (String console : List.of(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))) {
            assertThat(console).doesNotContain(REFERENCE_ANSWER).doesNotContain(GROUNDING_EXCERPT);
        }
    }

    @Test
    void anUnexpectedFailureReportsTheRunFailedExitCode() {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        when(quizzes.start(KB_ID, USER_ID, new StudyScope.WholeKnowledgeBase()))
            .thenThrow(new IllegalStateException("database down"));
        QuizCommandRunner runner = makeRunner("");

        runner.run(makeArgs());

        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    /** A session of {@code size} questions; question {@code i} scores {@code i - 1} with feedback "Feedback i.". */
    private void givenAQuizOf(int size) {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        when(quizzes.start(KB_ID, USER_ID, new StudyScope.WholeKnowledgeBase())).thenReturn(makeSession(size));
        int[] cursor = {0};
        when(quizzes.next(KB_ID, USER_ID, SESSION_ID)).thenAnswer(call -> cursor[0] >= size
            ? Optional.empty()
            : Optional.of(new NextQuestion(cursor[0] + 1, size, "Question " + (cursor[0] + 1) + "?")));
        when(quizzes.answer(eq(KB_ID), eq(USER_ID), eq(SESSION_ID), anyString())).thenAnswer(call -> {
            cursor[0]++;
            return new Graded(new QuizEvaluation(cursor[0] - 1, "Feedback " + cursor[0] + "."), cursor[0] >= size);
        });
    }

    private static QuizSession makeSession(int size) {
        List<QuizQuestion> questions = new ArrayList<>();
        IntStream.rangeClosed(1, size).forEach(i -> questions.add(new QuizQuestion(UUID.randomUUID(), "section",
            "Question " + i + "?", REFERENCE_ANSWER, GROUNDING_EXCERPT)));
        return new QuizSession(SESSION_ID, KB_ID, USER_ID, questions, 0, Instant.now().plusSeconds(3600));
    }

    private QuizCommandRunner makeRunner(String stdin) {
        return new QuizCommandRunner(accounts, knowledgeBases, quizzes,
            new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static DefaultApplicationArguments makeArgs() {
        return new DefaultApplicationArguments("--spring.profiles.active=cli", "quiz", "--kb=" + KB_ID,
            "--user=" + EMAIL);
    }

    private List<String> stderrLines() {
        return err.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private static User makeUser() {
        return new User(USER_ID, "Ada", EMAIL, null, null, null, null);
    }
}
