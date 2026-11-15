package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.mindforge.slack.SlackRateLimiter;

class SlackRateLimiterTest {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String USER = "U0ADA";

    // AC3

    @Test
    void limitCallsPass_andTheNextIsRejectedWithTheSecondsUntilTheWindowResets() {
        MutableClock clock = new MutableClock();
        SlackRateLimiter limiter = makeLimiter(clock);

        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(USER)).isEmpty();
        }
        clock.advance(Duration.ofMillis(20_500));

        assertThat(limiter.tryAcquire(USER)).hasValue(40);
    }

    @Test
    void theRetrySeconds_areAtLeastOne() {
        MutableClock clock = new MutableClock();
        SlackRateLimiter limiter = makeLimiter(clock);
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(USER);
        }
        clock.advance(WINDOW.minusMillis(1));

        assertThat(limiter.tryAcquire(USER)).hasValue(1);
    }

    @Test
    void theAllowance_resetsAfterTheWindow() {
        MutableClock clock = new MutableClock();
        SlackRateLimiter limiter = makeLimiter(clock);
        for (int i = 0; i <= LIMIT; i++) {
            limiter.tryAcquire(USER);
        }
        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire(USER)).isEmpty();
    }

    @Test
    void twoUsers_haveSeparateCounters() {
        SlackRateLimiter limiter = makeLimiter(new MutableClock());
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire(USER);
        }

        assertThat(limiter.tryAcquire("U0BOB")).isEmpty();
        assertThat(limiter.tryAcquire(USER)).isPresent();
    }

    private static SlackRateLimiter makeLimiter(Clock clock) {
        return new SlackRateLimiter(LIMIT, WINDOW, clock);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return now; }
    }
}
