package dev.mindforge.slack;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.QuizService.Graded;
import dev.mindforge.application.service.QuizService.NextQuestion;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.QuizFinishedException;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.slack.SlackQuizSessions.ActiveQuiz;

/**
 * {@code /mf-quiz start} and {@code /mf-quiz answer <text>}: a Quiz Session over the caller's only knowledge base
 * (D05). The verb is the first word of the command text. The caller's current quiz lives in {@link SlackQuizSessions};
 * ownership stays in {@link QuizService}. A reply carries only a question, a score and feedback, never anything else
 * of the session.
 */
public class SlackQuizCommand implements SlackCommand {

    private static final Logger log = LoggerFactory.getLogger(SlackQuizCommand.class);

    static final String NAME = "/mf-quiz";
    static final String USAGE = "Usage: /mf-quiz start, or /mf-quiz answer <text> (at most 4000 characters).";
    static final String NO_ACTIVE_QUIZ = "No active quiz. Run /mf-quiz start.";
    static final String NOTHING_TO_ASK = "This knowledge base has nothing to quiz on yet.";
    static final String FINISHED = "Quiz finished.";

    private static final String START = "start";
    private static final String ANSWER = "answer";

    /** The same limit as the web's answer request. */
    private static final int MAX_ANSWER_LENGTH = 4000;

    private final QuizService quizService;
    private final SlackKnowledgeBases knowledgeBases;
    private final SlackQuizSessions quizzes;

    public SlackQuizCommand(QuizService quizService, SlackKnowledgeBases knowledgeBases, SlackQuizSessions quizzes) {
        this.quizService = quizService;
        this.knowledgeBases = knowledgeBases;
        this.quizzes = quizzes;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void handle(SlackCall call, UUID userId, SlackReply reply) {
        String[] words = (call.text() == null ? "" : call.text().strip()).split("\\s+", 2);
        String verb = words[0].toLowerCase(Locale.ROOT);
        String rest = words.length > 1 ? words[1].strip() : "";
        try {
            switch (verb) {
                case START -> start(userId, reply);
                case ANSWER -> answer(rest, userId, reply);
                default -> reply.send(USAGE);
            }
        } catch (AIGatewayUnavailableException | DeadlineExceededException | ModelOutputException e) {
            log.warn("Slack /mf-quiz failed: verb={} error={}", verb, e.getClass().getName());
            reply.send(SlackAskCommand.MODEL_UNAVAILABLE);
        }
    }

    private void start(UUID userId, SlackReply reply) {
        Optional<UUID> kbId = knowledgeBases.resolveKnowledgeBase(userId, reply);
        if (kbId.isEmpty()) {
            return;
        }
        UUID sessionId;
        try {
            sessionId = quizService.start(kbId.get(), userId, new StudyScope.WholeKnowledgeBase()).sessionId();
        } catch (NotFoundException e) {
            reply.send(NOTHING_TO_ASK);
            return;
        }
        quizzes.put(userId, new ActiveQuiz(kbId.get(), sessionId));
        reply.send(nextOrFinished(kbId.get(), userId, sessionId));
    }

    private void answer(String answer, UUID userId, SlackReply reply) {
        if (answer.isEmpty() || answer.length() > MAX_ANSWER_LENGTH) {
            reply.send(USAGE);
            return;
        }
        Optional<ActiveQuiz> quiz = quizzes.current(userId);
        if (quiz.isEmpty()) {
            reply.send(NO_ACTIVE_QUIZ);
            return;
        }
        UUID kbId = quiz.get().kbId();
        UUID sessionId = quiz.get().sessionId();
        String text;
        try {
            Graded graded = quizService.answer(kbId, userId, sessionId, answer);
            text = "Score: " + graded.evaluation().score() + "/5\n" + graded.evaluation().feedback() + "\n\n";
            if (graded.finished()) {
                quizzes.clear(userId);
                text += FINISHED;
            } else {
                text += nextOrFinished(kbId, userId, sessionId);
            }
        } catch (NotFoundException | NotOwnerException | QuizFinishedException e) {
            quizzes.clear(userId);
            reply.send(NO_ACTIVE_QUIZ);
            return;
        }
        reply.send(text);
    }

    private String nextOrFinished(UUID kbId, UUID userId, UUID sessionId) {
        Optional<NextQuestion> next = quizService.next(kbId, userId, sessionId);
        if (next.isEmpty()) {
            quizzes.clear(userId);
            return FINISHED;
        }
        NextQuestion question = next.get();
        return "Question " + question.index() + "/" + question.total() + ": " + question.question();
    }
}
