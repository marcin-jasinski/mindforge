package dev.mindforge.domain.model;

/**
 * Thrown when a deterministic lesson identity cannot be resolved or the resolved
 * identifier violates the lesson-id format rules. Lesson identity never falls back
 * to a placeholder such as {@code "unknown"} — failure is explicit.
 */
public class LessonIdentityException extends IllegalArgumentException {

    public LessonIdentityException(String message) {
        super(message);
    }
}
