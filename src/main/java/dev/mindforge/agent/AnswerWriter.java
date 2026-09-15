package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.AnswerDraft;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Answers a question from the pages given, citing them by path. It never writes a page. */
public class AnswerWriter {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public AnswerWriter(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public AnswerDraft answer(String question, String priorTurns, Map<String, String> pagesByPath) {
        String pages = pagesByPath.isEmpty() ? "(brak)" : pagesByPath.entrySet().stream()
            .map(page -> "=== " + page.getKey() + "\n" + page.getValue())
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("answer_writer", Map.of("pages", pages, "prior", priorTurns,
            "question", question));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.INTERACTIVE).content(), Output.class);
        if (output.answer() == null || output.answer().isBlank()) {
            throw new ModelOutputException(getClass().getSimpleName(), new IllegalArgumentException("no answer"));
        }
        return new AnswerDraft(output.answer().strip(), output.citations());
    }

    record Output(String answer, List<String> citations) {}
}
