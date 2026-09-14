package dev.mindforge.agent;

import java.util.Map;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.model.ValidationResult;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Decides whether an uploaded document is learning material at all, before anything is extracted from it. */
public class RelevanceGuard {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.SMALL;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public RelevanceGuard(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public ValidationResult check(String lessonTitle, String sample) {
        String prompt = prompts.render("relevance_guard", Map.of("title", lessonTitle, "document", sample));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BACKGROUND).content(), Output.class);
        if (output.relevant() == null) {
            throw new ModelOutputException(getClass().getSimpleName(), new IllegalArgumentException("no verdict"));
        }
        String reason = output.reason() == null ? "" : TextRules.singleLine(output.reason());
        return new ValidationResult(output.relevant(), reason, output.confidence());
    }

    record Output(Boolean relevant, String reason, float confidence) {}
}
