package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.slack.SlackQuizSessions;
import dev.mindforge.slack.SlackQuizSessions.ActiveQuiz;

class SlackQuizSessionsTest {

    private final SlackQuizSessions sessions = new SlackQuizSessions(Duration.ofHours(2));

    // AC1

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

    @Test
    void anEntryOlderThanTheTtl_isGone() {
        SlackQuizSessions expiring = new SlackQuizSessions(Duration.ZERO);
        UUID user = UUID.randomUUID();

        expiring.put(user, makeQuiz());

        assertThat(expiring.current(user)).isEmpty();
    }

    private static ActiveQuiz makeQuiz() {
        return new ActiveQuiz(UUID.randomUUID(), UUID.randomUUID());
    }
}
