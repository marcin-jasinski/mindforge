package dev.mindforge.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.QuizSession;

/** Server-held quiz sessions, reference answers included. */
public interface QuizSessionStore {

    void insert(UUID kbId, QuizSession session);

    /** An unexpired session. */
    Optional<QuizSession> find(UUID kbId, UUID sessionId, Instant now);

    void updateCursor(UUID kbId, UUID sessionId, int cursor);

    // ---------------------------------------------------------------------------
    // System method: for the scheduled cleanup only, which acts for no user
    // ---------------------------------------------------------------------------

    int deleteExpired(Instant now);
}
