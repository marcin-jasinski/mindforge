package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.discord.DiscordQuizSessions;
import dev.mindforge.discord.DiscordQuizSessions.ActiveQuiz;

class DiscordQuizSessionsTest {

    private final DiscordQuizSessions sessions = new DiscordQuizSessions(Duration.ofHours(2));

    @Test
    void anEntryPut_isTheUsersCurrentQuizOnly() {
        UUID user = UUID.randomUUID();
        ActiveQuiz quiz = makeQuiz();

        sessions.put(user, quiz);

        assertThat(sessions.current(user)).contains(quiz);
        assertThat(sessions.current(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aSecondPut_replacesTheEntry() {
        UUID user = UUID.randomUUID();
        ActiveQuiz second = makeQuiz();

        sessions.put(user, makeQuiz());
        sessions.put(user, second);

        assertThat(sessions.current(user)).contains(second);
    }

    @Test
    void clear_removesOnlyThatUsersEntry() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ActiveQuiz otherQuiz = makeQuiz();
        sessions.put(user, makeQuiz());
        sessions.put(other, otherQuiz);

        sessions.clear(user);

        assertThat(sessions.current(user)).isEmpty();
        assertThat(sessions.current(other)).contains(otherQuiz);
    }

    private static ActiveQuiz makeQuiz() {
        return new ActiveQuiz(UUID.randomUUID(), UUID.randomUUID());
    }
}
