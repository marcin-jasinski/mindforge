package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * An authenticated platform user. {@code passwordHash} is a server-only field and
 * must never be exposed through an API response.
 */
public record User(
    UUID userId,
    String displayName,
    String email,
    String passwordHash,
    String avatarUrl,
    Instant createdAt,
    Instant lastLoginAt
) {}
