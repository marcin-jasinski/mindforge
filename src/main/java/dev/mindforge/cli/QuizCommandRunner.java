package dev.mindforge.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.QuizService.Graded;
import dev.mindforge.application.service.QuizService.NextQuestion;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.User;

/**
 * The {@code quiz} command: starts a quiz over the whole knowledge base as a named user, through the same
 * {@link QuizService} calls as the web, asks each question on stdout, grades one stdin line per question and prints
 * a summary. EOF on stdin ends the quiz with the summary. Only {@link NextQuestion} and {@link Graded} are printed:
 * the session's reference answers and grounding excerpts never reach the console or the log.
 */
public class QuizCommandRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(QuizCommandRunner.class);

    private final AccountService accounts;
    private final KnowledgeBaseService knowledgeBases;
    private final QuizService quizzes;
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;
    private volatile CliExit exit = CliExit.OK;

    public QuizCommandRunner(AccountService accounts, KnowledgeBaseService knowledgeBases, QuizService quizzes,
                             InputStream in, PrintStream out, PrintStream err) {
        this.accounts = accounts;
        this.knowledgeBases = knowledgeBases;
        this.quizzes = quizzes;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!CliCommand.of(args).filter(CliCommand.QUIZ::equals).isPresent()) {
            return;
        }
        String kb = option(args, "kb");
        String email = option(args, "user");
        UUID kbId = kb == null ? null : parseUuid(kb);
        if (email == null || kbId == null) {
            err.print(CliUsageRunner.USAGE);
            exit = CliExit.REFUSED;
            return;
        }
        try {
            exit = quiz(kbId, email);
        } catch (RuntimeException e) {
            log.error("The quiz command failed", e);
            err.println("The quiz command failed: " + e.getMessage());
            exit = CliExit.RUN_FAILED;
        }
    }

    @Override
    public int getExitCode() {
        return exit.code();
    }

    private CliExit quiz(UUID kbId, String email) {
        User user;
        try {
            user = accounts.byEmail(email);
        } catch (NotFoundException e) {
            return refuse("user not found: " + email);
        }
        UUID userId = user.userId();
        try {
            knowledgeBases.requireOwner(kbId, userId);
        } catch (NotFoundException | NotOwnerException e) {
            return refuse("knowledge base not found or not owned by " + email);
        }
        UUID sessionId;
        try {
            sessionId = quizzes.start(kbId, userId, new StudyScope.WholeKnowledgeBase()).sessionId();
        } catch (NotFoundException e) {
            return refuse("nothing to quiz: " + e.getMessage());
        }
        ask(kbId, userId, sessionId);
        return CliExit.OK;
    }

    private void ask(UUID kbId, UUID userId, UUID sessionId) {
        BufferedReader answers = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        int answered = 0;
        int total = 0;
        int questions = 0;
        while (true) {
            Optional<NextQuestion> next = quizzes.next(kbId, userId, sessionId);
            if (next.isEmpty()) {
                break;
            }
            NextQuestion question = next.get();
            questions = question.total();
            out.printf("Question %d/%d: %s%n", question.index(), question.total(), question.question());
            String line = readLine(answers);
            if (line == null) {
                break;
            }
            Graded graded = quizzes.answer(kbId, userId, sessionId, line);
            answered++;
            total += graded.evaluation().score();
            out.println("Score: " + graded.evaluation().score() + "/5");
            out.println(graded.evaluation().feedback());
            if (graded.finished()) {
                break;
            }
        }
        String mean = answered == 0 ? "-" : String.format(Locale.ROOT, "%.1f/5", (double) total / answered);
        out.printf("Quiz ended: %d of %d questions answered, total score %d, mean score %s%n",
            answered, questions, total, mean);
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CliExit refuse(String message) {
        err.println(message);
        return CliExit.REFUSED;
    }

    private static String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            return null;
        }
        return values.getFirst();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
