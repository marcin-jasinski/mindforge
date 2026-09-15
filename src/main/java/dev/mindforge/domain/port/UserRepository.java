package dev.mindforge.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.User;

/** Accounts. Not tenant-scoped: a user owns knowledge bases rather than living in one. */
public interface UserRepository {

    Optional<User> findById(UUID userId);

    /** Emails are compared as stored, which is lowercased. */
    Optional<User> findByEmail(String email);

    User insert(User user);

    void recordLogin(UUID userId, Instant at);

    User updateDisplayName(UUID userId, String displayName);
}
