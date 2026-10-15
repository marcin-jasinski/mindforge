package dev.mindforge.api.dto.response;

/** The question text and its place in the quiz — nothing that would reveal the answer. */
public record NextQuestionResponse(int index, int total, String question) {}
