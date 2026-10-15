package dev.mindforge.api.config;

import java.util.UUID;

/** The signed-in user, as the session cookie names them. */
public record CurrentUser(UUID userId) {}
