package dev.mindforge.application.study;

import java.time.Duration;
import java.time.Instant;

import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.ReviewResult;

/**
 * SM-2: a rating below 3 resets repetitions and reschedules for tomorrow; otherwise the interval grows 1, 6, then
 * by the ease factor. {@code EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))}, never below 1.3.
 */
public final class SM2Scheduler {

    public static final double INITIAL_EASE = 2.5;
    public static final double MIN_EASE = 1.3;

    private SM2Scheduler() {}

    public static CardState review(CardState card, ReviewResult result, Instant now) {
        int q = result.rating();
        double ease = Math.max(MIN_EASE, card.easeFactor() + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)));
        int repetitions;
        int interval;
        if (q < 3) {
            repetitions = 0;
            interval = 1;
        } else {
            repetitions = card.repetitions() + 1;
            interval = switch (repetitions) {
                case 1 -> 1;
                case 2 -> 6;
                default -> (int) Math.round(card.intervalDays() * ease);
            };
        }
        return new CardState(card.card(), ease, interval, repetitions, now.plus(Duration.ofDays(interval)),
            card.retiredAt());
    }
}
