package dev.mindforge.domain.model;

/**
 * The outcome of a validation step (e.g. the relevance guard): whether the check
 * passed, a human-readable reason, and the model's confidence in the decision.
 */
public record ValidationResult(
    boolean passed,
    String reason,
    float confidence
) {}
