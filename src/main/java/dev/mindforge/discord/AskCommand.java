package dev.mindforge.discord;

import java.util.Optional;
import java.util.UUID;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
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
 * {@code /ask}: a one-turn Query interaction (D06). Each call starts a new interaction and asks once; the adapter
 * keeps no interaction state, so a later {@code /ask} has no follow-up context. The reply carries only the answer
 * and the cited page paths.
 */
public class AskCommand implements DiscordCommand {

    private static final Logger log = LoggerFactory.getLogger(AskCommand.class);

    static final String NAME = "ask";
    static final String QUESTION = "question";
    static final String MODEL_UNAVAILABLE = "The model is not available now. Try again later.";

    /** The same limit as the web's question request. */
    private static final int MAX_QUESTION_LENGTH = 4000;

    private final QueryService queryService;
    private final DiscordKnowledgeBases knowledgeBases;

    public AskCommand(QueryService queryService, DiscordKnowledgeBases knowledgeBases) {
        this.queryService = queryService;
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash(NAME, "Ask a question of one of your knowledge bases")
            .addOptions(
                DiscordKnowledgeBases.option(),
                new OptionData(OptionType.STRING, QUESTION, "Your question", true).setMaxLength(MAX_QUESTION_LENGTH));
    }

    @Override
    public void handle(DiscordCall call, UUID userId, DiscordReply reply) {
        Optional<UUID> kbId = knowledgeBases.resolveKnowledgeBase(call.options().get(DiscordKnowledgeBases.OPTION),
            userId, reply);
        if (kbId.isEmpty()) {
            return;
        }
        QueryService.Answer answer;
        try {
            Interaction interaction = queryService.start(kbId.get(), userId);
            answer = queryService.ask(kbId.get(), userId, interaction.interactionId(), call.options().get(QUESTION));
        } catch (NotOwnerException | NotFoundException e) {
            reply.send(DiscordKnowledgeBases.UNKNOWN);
            return;
        } catch (AIGatewayUnavailableException | DeadlineExceededException | ModelOutputException e) {
            log.warn("Discord /ask failed: error={}", e.getClass().getName());
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
