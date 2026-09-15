package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Picks the pages a question needs from the rendered index; the caller verifies every path it returns. */
public class PageSelector {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.SMALL;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public PageSelector(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public List<String> select(String question, String priorTurns, String renderedIndex, int maxPages) {
        String prompt = prompts.render("page_selector", Map.of("index", renderedIndex, "prior", priorTurns,
            "question", question, "maxPages", String.valueOf(maxPages)));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.INTERACTIVE).content(), Output.class);
        return output.paths() == null ? List.of()
            : output.paths().stream().filter(Objects::nonNull).distinct().limit(maxPages).toList();
    }

    record Output(List<String> paths) {}
}
