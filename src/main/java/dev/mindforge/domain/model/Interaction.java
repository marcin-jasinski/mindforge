package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One Query conversation of a user in a knowledge base. */
public record Interaction(UUID interactionId, UUID knowledgeBaseId, UUID userId, Instant startedAt) {}
