package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.LinkInsertion;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Proposes links to wrap around phrases already in pages; code decides which proposals apply. */
public class LinkChecker {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.SMALL;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public LinkChecker(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public List<LinkInsertion> check(Map<String, String> bodiesByPath, String linkableIndex) {
        String pages = bodiesByPath.entrySet().stream()
            .map(page -> "=== " + page.getKey() + "\n" + page.getValue())
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("link_checker", Map.of("index", linkableIndex, "pages", pages));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BACKGROUND).content(), Output.class);
        return output.insertions() == null ? List.of() : output.insertions();
    }

    record Output(List<LinkInsertion> insertions) {}
}
