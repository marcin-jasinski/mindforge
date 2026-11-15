package dev.mindforge.discord;

import java.util.Optional;
import java.util.UUID;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.QuizService.Graded;
import dev.mindforge.application.service.QuizService.NextQuestion;
import dev.mindforge.discord.DiscordQuizSessions.ActiveQuiz;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.QuizFinishedException;
import dev.mindforge.domain.model.StudyScope;

/**
 * {@code /quiz start} and {@code /quiz answer}: a Quiz Session over a whole knowledge base (D05). The caller's current
 * quiz lives in {@link DiscordQuizSessions}; ownership stays in {@link QuizService}. A reply carries only a question, a
 * score and feedback, never anything else of the session.
 */
public class QuizCommand implements DiscordCommand {

    private static final Logger log = LoggerFactory.getLogger(QuizCommand.class);

    static final String NAME = "quiz";
    static final String START = "start";
    static final String ANSWER = "answer";
    static final String TEXT = "text";
    static final String NO_ACTIVE_QUIZ = "No active quiz. Run /quiz start.";
    static final String NOTHING_TO_ASK = "This knowledge base has nothing to quiz on yet.";
    static final String FINISHED = "Quiz finished.";

    /** The same limit as the web's answer request. */
    private static final int MAX_ANSWER_LENGTH = 4000;

    private final QuizService quizService;
    private final DiscordKnowledgeBases knowledgeBases;
    private final DiscordQuizSessions quizzes;

    public QuizCommand(QuizService quizService, DiscordKnowledgeBases knowledgeBases, DiscordQuizSessions quizzes) {
        this.quizService = quizService;
        this.knowledgeBases = knowledgeBases;
        this.quizzes = quizzes;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash(NAME, "Take a quiz on one of your knowledge bases")
            .addSubcommands(
                new SubcommandData(START, "Start a quiz on a whole knowledge base")
                    .addOptions(DiscordKnowledgeBases.option()),
                new SubcommandData(ANSWER, "Answer the current question of your quiz")
                    .addOptions(new OptionData(OptionType.STRING, TEXT, "Your answer", true)
                        .setMaxLength(MAX_ANSWER_LENGTH)));
    }

    @Override
    public void handle(DiscordCall call, UUID userId, DiscordReply reply) {
        try {
            switch (call.subcommand() == null ? "" : call.subcommand()) {
                case START -> start(call, userId, reply);
                case ANSWER -> answer(call, userId, reply);
                default -> {
                    log.warn("Discord /quiz subcommand unknown: subcommand={}", call.subcommand());
                    reply.send(DiscordCommandHandler.FAILED);
                }
            }
        } catch (AIGatewayUnavailableException | DeadlineExceededException | ModelOutputException e) {
            log.warn("Discord /quiz failed: subcommand={} error={}", call.subcommand(), e.getClass().getName());
            reply.send(AskCommand.MODEL_UNAVAILABLE);
        }
    }

    private void start(DiscordCall call, UUID userId, DiscordReply reply) {
        Optional<UUID> kbId = knowledgeBases.resolveKnowledgeBase(call.options().get(DiscordKnowledgeBases.OPTION),
            userId, reply);
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

    private void answer(DiscordCall call, UUID userId, DiscordReply reply) {
        Optional<ActiveQuiz> quiz = quizzes.current(userId);
        if (quiz.isEmpty()) {
            reply.send(NO_ACTIVE_QUIZ);
            return;
        }
        UUID kbId = quiz.get().kbId();
        UUID sessionId = quiz.get().sessionId();
        String text;
        try {
            Graded graded = quizService.answer(kbId, userId, sessionId, call.options().get(TEXT));
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
