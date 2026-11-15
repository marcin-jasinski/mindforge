package dev.mindforge.slack;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caps how many model-spending commands a Slack user may run per window. It copies the window logic of
 * {@code RateLimitInterceptor} and {@code DiscordRateLimiter}, with its own counter keyed by the Slack user id.
 * In-process: the deployment has one live instance.
 */
public class SlackRateLimiter {

    private record Window(long startMillis, int count) {}

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Cache<String, Window> windows;

    public SlackRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
        this.windows = Caffeine.newBuilder().expireAfterWrite(window).build();
    }

    /** Empty when the command may run; otherwise the seconds until the user's window resets (at least 1). */
    public OptionalLong tryAcquire(String slackUserId) {
        long now = clock.millis();
        Window window = windows.asMap().compute(slackUserId, (id, old) ->
            old == null || now - old.startMillis() >= windowMillis
                ? new Window(now, 1)
                : new Window(old.startMillis(), old.count() + 1));
        if (window.count() <= limit) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(1, (window.startMillis() + windowMillis - now + 999) / 1000));
    }
}
