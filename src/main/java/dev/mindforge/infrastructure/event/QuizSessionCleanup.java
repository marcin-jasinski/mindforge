package dev.mindforge.infrastructure.event;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;

import dev.mindforge.domain.port.QuizSessionStore;

/** Deletes expired quiz sessions, and with them their reference answers. */
public class QuizSessionCleanup {

    private final QuizSessionStore sessions;

    public QuizSessionCleanup(QuizSessionStore sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "${mindforge.study.session-cleanup-interval:PT15M}")
    public void deleteExpired() {
        sessions.deleteExpired(Instant.now());
    }
}
