package dev.mindforge.domain.port;

import dev.mindforge.domain.model.CompletionResult;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;

public interface AIGateway {

    CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline);

    float[] embed(String text);
}
