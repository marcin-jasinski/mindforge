package dev.mindforge.domain.model;

/** A past turn as a user's history shows it: the question and the answer, nothing the server used to write it. */
public record TurnSummary(String question, String answer) {}
