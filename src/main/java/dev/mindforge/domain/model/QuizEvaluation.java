package dev.mindforge.domain.model;

/** How an answer was graded: a 0–5 score and feedback for the learner. */
public record QuizEvaluation(int score, String feedback) {}
