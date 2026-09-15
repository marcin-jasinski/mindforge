package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when an answer is sent to a quiz session that has no questions left. */
public class QuizFinishedException extends IllegalStateException {

    public QuizFinishedException(UUID sessionId) {
        super("Quiz session " + sessionId + " has no questions left");
    }
}
