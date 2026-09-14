package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.PageDraft;
import dev.mindforge.domain.model.PageWriteTask;
import dev.mindforge.domain.model.SupersededSection;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Writes one page's description and body from its task. It returns no title; the caller validates the draft. */
public class PageWriter {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private static final String NONE = "(brak)";

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public PageWriter(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /**
     * @param existingBody the live body verbatim, or null for a create
     * @param keepSections whether every existing level-1 section must survive, as for a document revising a Concept
     */
    public PageDraft write(PageWriteTask task, String sourceText, String existingBody,
                           List<SupersededSection> superseded, String linkableIndex, boolean keepSections) {
        String claims = task.claims().isEmpty() ? NONE : task.claims().stream()
            .map(claim -> "* " + claim.text())
            .collect(Collectors.joining("\n"));
        String supersededList = superseded.isEmpty() ? NONE : superseded.stream()
            .map(section -> "* „" + section.heading() + "” (#" + section.anchor() + ") — poprawione przez /"
                + section.supersedingPath() + ".md („" + section.supersedingTitle() + "”)")
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("page_writer", Map.of(
            "type", task.type().value(),
            "path", task.path(),
            "title", task.title(),
            "claims", claims,
            "source", sourceText.isBlank() ? NONE : sourceText,
            "existingBody", existingBody == null ? "(nowa strona)" : existingBody,
            "superseded", supersededList,
            "sectionRule", keepSections
                ? "Zachowaj każdą istniejącą sekcję poziomu 1 (także poprawione): jej nagłówek może zmienić tylko"
                    + " wielkość liter, znaki diakrytyczne lub interpunkcję. Możesz dodawać nowe sekcje."
                : "Możesz zmienić układ sekcji.",
            "index", linkableIndex));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BACKGROUND).content(), Output.class);
        if (output.description() == null || output.body() == null) {
            throw new ModelOutputException(getClass().getSimpleName(),
                new IllegalArgumentException("a draft needs a description and a body"));
        }
        return new PageDraft(output.description(), output.body());
    }

    record Output(String description, String body) {}
}
