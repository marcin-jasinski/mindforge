package dev.mindforge.slack;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Each Slack user's current quiz (D05), keyed by the internal user id, never the Slack id. In-process: the deployment
 * has one live instance, and an entry lost on restart only means the user runs {@code /mf-quiz start} again.
 */
public class SlackQuizSessions {

    /** The quiz a user's {@code /mf-quiz answer} goes to. */
    public record ActiveQuiz(UUID kbId, UUID sessionId) {}

    private final Cache<UUID, ActiveQuiz> quizzes;

    public SlackQuizSessions(Duration ttl) {
        this.quizzes = Caffeine.newBuilder().expireAfterWrite(ttl).build();
    }

    /** Makes the quiz the user's current one, replacing any earlier one. */
    public void put(UUID userId, ActiveQuiz quiz) {
        quizzes.put(userId, quiz);
    }

    public Optional<ActiveQuiz> current(UUID userId) {
        return Optional.ofNullable(quizzes.getIfPresent(userId));
    }

    public void clear(UUID userId) {
        quizzes.invalidate(userId);
    }
}
