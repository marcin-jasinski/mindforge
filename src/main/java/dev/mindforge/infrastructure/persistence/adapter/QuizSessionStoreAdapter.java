package dev.mindforge.infrastructure.persistence.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.infrastructure.persistence.jpa.QuizSessionJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.StudyEntityMapper;

/**
 * Quiz sessions in PostgreSQL with a Caffeine cache in front, so a quiz's reads stay in memory while a restart loses
 * nothing. Ceiling: one instance — a second would read a stale cursor from its own cache.
 */
@Transactional
public class QuizSessionStoreAdapter implements QuizSessionStore {

    private static final int CACHED_SESSIONS = 10_000;

    private final QuizSessionJpaRepository sessions;
    private final StudyEntityMapper mapper;
    private final Cache<UUID, QuizSession> cache;

    public QuizSessionStoreAdapter(QuizSessionJpaRepository sessions, StudyEntityMapper mapper, Duration ttl) {
        this.sessions = sessions;
        this.mapper = mapper;
        this.cache = Caffeine.newBuilder().maximumSize(CACHED_SESSIONS).expireAfterWrite(ttl).build();
    }

    @Override
    public void insert(UUID kbId, QuizSession session) {
        sessions.saveAndFlush(mapper.toEntity(session));
        cache.put(session.sessionId(), session);
    }

    @Override
    public Optional<QuizSession> find(UUID kbId, UUID sessionId, Instant now) {
        QuizSession cached = cache.getIfPresent(sessionId);
        if (cached != null) {
            return Optional.of(cached).filter(s -> s.knowledgeBaseId().equals(kbId) && s.expiresAt().isAfter(now));
        }
        Optional<QuizSession> stored = sessions.findByKnowledgeBaseIdAndSessionIdAndExpiresAtAfter(kbId, sessionId, now)
            .map(mapper::toDomain);
        stored.ifPresent(session -> cache.put(sessionId, session));
        return stored;
    }

    @Override
    public void updateCursor(UUID kbId, UUID sessionId, int cursor) {
        sessions.updateCursor(kbId, sessionId, cursor);
        cache.invalidate(sessionId);
    }

    @Override
    public int deleteExpired(Instant now) {
        return sessions.deleteExpired(now);
    }
}
