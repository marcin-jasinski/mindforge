package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.ReviewItem;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/**
 * Reviews a chunk of Concept pages for what Lint reports but never fixes. Items of an unknown kind or without text are
 * dropped; which pages they name is checked by the caller.
 */
public class WikiReviewer {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public WikiReviewer(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public List<ReviewItem> review(Map<String, String> bodiesByPath, String renderedIndex) {
        String pages = bodiesByPath.entrySet().stream()
            .map(page -> "=== " + page.getKey() + "\n" + page.getValue())
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("wiki_reviewer", Map.of("index", renderedIndex, "pages", pages));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BACKGROUND).content(), Output.class);
        return output.items() == null ? List.of() : output.items().stream()
            .filter(item -> item.kind() != null && item.text() != null && !item.text().isBlank()
                && (ReviewItem.FINDINGS.contains(item.kind()) || ReviewItem.SUGGESTIONS.contains(item.kind())))
            .map(item -> new ReviewItem(item.kind(),
                item.pages() == null ? List.of() : item.pages().stream().filter(Objects::nonNull).toList(),
                TextRules.singleLine(item.text())))
            .toList();
    }

    record Output(List<ItemOutput> items) {}

    record ItemOutput(String kind, List<String> pages, String text) {}
}
