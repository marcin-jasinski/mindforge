package dev.mindforge.agent;

import java.util.Map;

import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.QuizEvaluation;
import dev.mindforge.domain.model.ReviewResult;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Grades an answer against the session's reference answer on SM-2's 0–5 rubric; code clamps the score (T27). */
public class QuizEvaluator {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.SMALL;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public QuizEvaluator(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public QuizEvaluation grade(String question, String referenceAnswer, String groundingExcerpt, String answer) {
        String prompt = prompts.render("quiz_evaluator", Map.of("question", question, "reference", referenceAnswer,
            "grounding", groundingExcerpt == null ? "(brak)" : groundingExcerpt, "answer", answer));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.INTERACTIVE).content(), Output.class);
        if (output.score() == null) {
            throw new ModelOutputException(getClass().getSimpleName(), new IllegalArgumentException("no score"));
        }
        return new QuizEvaluation(ReviewResult.clamped(output.score()).rating(),
            output.feedback() == null ? "" : TextRules.singleLine(output.feedback()));
    }

    record Output(Integer score, String feedback) {}
}
