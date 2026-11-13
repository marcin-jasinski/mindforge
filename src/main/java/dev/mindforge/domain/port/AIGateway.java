package dev.mindforge.domain.port;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ModelTier;

public interface AIGateway {

    CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline);

    /** One single-shot completion over the prompt and one image; only {@link ModelTier#VISION} takes an image. */
    CompletionResult complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile deadline);
}
