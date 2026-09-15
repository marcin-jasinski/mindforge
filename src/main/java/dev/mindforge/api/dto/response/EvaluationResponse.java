package dev.mindforge.api.dto.response;

/** A graded answer: a 0–5 score, feedback, and whether the quiz is over. */
public record EvaluationResponse(int score, String feedback, boolean finished) {}
