package dev.mindforge.discord;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Each Discord user's current quiz (D05), keyed by the internal user id. In-process: the deployment has one live
 * instance, and an entry lost on restart only means the user runs {@code /quiz start} again.
 */
public class DiscordQuizSessions {

    /** The quiz a user's {@code /quiz answer} goes to. */
    public record ActiveQuiz(UUID kbId, UUID sessionId) {}

    private final Cache<UUID, ActiveQuiz> quizzes;

    public DiscordQuizSessions(Duration ttl) {
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
