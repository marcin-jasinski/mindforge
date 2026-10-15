package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.study.SM2Scheduler;
import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.ReviewResult;

class SM2SchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Test
    void shouldGrowTheIntervalAndTheEaseOnPerfectRecall() {
        CardState first = SM2Scheduler.review(makeCard(2.5, 0, 0), new ReviewResult(5), NOW);
        CardState second = SM2Scheduler.review(first, new ReviewResult(5), NOW);
        CardState third = SM2Scheduler.review(second, new ReviewResult(5), NOW);

        assertThat(first.easeFactor()).isCloseTo(2.6, within(1e-9));
        assertThat(first).extracting(CardState::repetitions, CardState::intervalDays).containsExactly(1, 1);
        assertThat(second).extracting(CardState::repetitions, CardState::intervalDays).containsExactly(2, 6);
        assertThat(third.intervalDays()).isEqualTo(17);
        assertThat(third.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(17)));
    }

    @Test
    void shouldResetRepetitionsOnFailedRecall() {
        CardState reviewed = SM2Scheduler.review(makeCard(2.5, 16, 3), new ReviewResult(0), NOW);

        assertThat(reviewed).extracting(CardState::repetitions, CardState::intervalDays).containsExactly(0, 1);
        assertThat(reviewed.easeFactor()).isCloseTo(1.7, within(1e-9));
    }

    @Test
    void shouldNeverLetTheEaseFallBelowItsFloorForAnyRating() {
        CardState card = makeCard(1.3, 1, 0);
        for (int rating = 0; rating <= 5; rating++) {
            assertThat(SM2Scheduler.review(card, new ReviewResult(rating), NOW).easeFactor())
                .isGreaterThanOrEqualTo(SM2Scheduler.MIN_EASE);
        }
    }

    private static CardState makeCard(double ease, int interval, int repetitions) {
        return new CardState(new Flashcard("abc", UUID.randomUUID(), null, CardType.BASIC, "P", "O", "hash"), ease,
            interval, repetitions, NOW, null);
    }
}
