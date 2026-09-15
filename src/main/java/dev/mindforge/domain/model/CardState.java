package dev.mindforge.domain.model;

import java.time.Instant;

/** A stored card with its SM-2 schedule; {@code retiredAt} is set once a regeneration stopped returning it. */
public record CardState(
    Flashcard card,
    double easeFactor,
    int intervalDays,
    int repetitions,
    Instant dueAt,
    Instant retiredAt
) {

    public boolean retired() {
        return retiredAt != null;
    }
}
