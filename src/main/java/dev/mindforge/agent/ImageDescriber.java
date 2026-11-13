package dev.mindforge.agent;

import java.util.Map;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Turns one image of a document into a line of text a reader can learn from, or says the image teaches nothing. */
public class ImageDescriber {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.VISION;

    static final String MARKER = "Image: ";
    static final int MAX_DESCRIPTION_CHARS = 1000;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public ImageDescriber(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /** Unusable model output is a result, never an exception; gateway and deadline errors propagate. */
    public Description describe(String lessonTitle, String context, ImageInput image) {
        String prompt = prompts.render("image_describer", Map.of("title", lessonTitle, "context", context));
        String completion = gateway.complete(TIER, prompt, image, DeadlineProfile.BACKGROUND).content();
        if (completion == null || completion.isBlank()) {
            return new Unusable();
        }
        Output output;
        try {
            output = ModelJson.read(getClass().getSimpleName(), completion, Output.class);
        } catch (ModelOutputException e) {
            return new Unusable();
        }
        if (output.relevant() == null) {
            return new Unusable();
        }
        if (!output.relevant()) {
            return new NotRelevant();
        }
        if (output.description() == null) {
            return new Unusable();
        }
        String description = TextRules.singleLine(output.description());
        if (description.isEmpty() || description.length() > MAX_DESCRIPTION_CHARS) {
            return new Unusable();
        }
        return new Described(MARKER + description);
    }

    /** What the model made of one image. */
    public sealed interface Description permits Described, NotRelevant, Unusable {}

    /** A relevant image; {@code text} already starts with the marker. */
    public record Described(String text) implements Description {}

    /** The model marked the image as having no learning content. */
    public record NotRelevant() implements Description {}

    /** The model's answer broke a rule of the prompt. */
    public record Unusable() implements Description {}

    private record Output(Boolean relevant, String description) {}
}
