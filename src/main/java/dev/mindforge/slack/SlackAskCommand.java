package dev.mindforge.slack;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.QueryService;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;

/**
 * {@code /mf-ask <question>}: a one-turn Query interaction on the caller's only knowledge base. Each call starts a new
 * interaction and asks once; the adapter keeps no interaction state. The reply carries only the answer and the cited
 * page paths.
 */
public class SlackAskCommand implements SlackCommand {

    private static final Logger log = LoggerFactory.getLogger(SlackAskCommand.class);

    static final String NAME = "/mf-ask";
    static final String USAGE = "Usage: /mf-ask <question> (at most 4000 characters).";
    static final String MODEL_UNAVAILABLE = "The model is not available now. Try again later.";

    /** The same limit as the web's question request. */
    private static final int MAX_QUESTION_LENGTH = 4000;

    private final QueryService queryService;
    private final SlackKnowledgeBases knowledgeBases;

    public SlackAskCommand(QueryService queryService, SlackKnowledgeBases knowledgeBases) {
        this.queryService = queryService;
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void handle(SlackCall call, UUID userId, SlackReply reply) {
        String question = call.text() == null ? "" : call.text().strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION_LENGTH) {
            reply.send(USAGE);
            return;
        }
        Optional<UUID> kbId = knowledgeBases.resolveKnowledgeBase(userId, reply);
        if (kbId.isEmpty()) {
            return;
        }
        QueryService.Answer answer;
        try {
            Interaction interaction = queryService.start(kbId.get(), userId);
            answer = queryService.ask(kbId.get(), userId, interaction.interactionId(), question);
        } catch (NotOwnerException | NotFoundException e) {
            reply.send(SlackKnowledgeBases.NOT_ONE);
            return;
        } catch (AIGatewayUnavailableException | DeadlineExceededException | ModelOutputException e) {
            log.warn("Slack /mf-ask failed: error={}", e.getClass().getName());
            reply.send(MODEL_UNAVAILABLE);
            return;
        }
        reply.send(text(answer));
    }

    private static String text(QueryService.Answer answer) {
        if (answer.citedPaths().isEmpty()) {
            return answer.answer();
        }
        StringBuilder text = new StringBuilder(answer.answer()).append("\n\nSources:");
        answer.citedPaths().forEach(path -> text.append("\n- ").append(path));
        return text.toString();
    }
}
