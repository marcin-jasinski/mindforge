package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * View of a {@link dev.mindforge.domain.model.User} for API responses.
 * Excludes {@code passwordHash} — must never leave the server.
 */
public record UserResponse(
    UUID userId,
    String displayName,
    String email,
    String avatarUrl,
    Instant createdAt,
    Instant lastLoginAt
) {}
