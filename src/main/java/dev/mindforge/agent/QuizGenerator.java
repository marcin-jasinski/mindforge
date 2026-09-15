package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.QuestionDraft;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Writes one batch of quiz questions over the pages shown, each with its reference answer and grounding excerpt. */
public class QuizGenerator {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public QuizGenerator(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /** @param bodiesByPath page bodies with superseded sections stripped, in targeting order */
    public List<QuestionDraft> generate(Map<String, String> bodiesByPath, int questions) {
        String pages = bodiesByPath.entrySet().stream()
            .map(page -> "=== " + page.getKey() + "\n" + page.getValue())
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("quiz_generator", Map.of("pages", pages, "count", String.valueOf(questions)));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BATCH).content(), Output.class);
        return output.questions() == null ? List.of() : output.questions();
    }

    record Output(List<QuestionDraft> questions) {}
}
