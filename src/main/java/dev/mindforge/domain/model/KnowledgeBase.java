package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-owned collection of documents and the wiki ingested from them. {@code pageCount} and {@code documentCount}
 * are derived at read.
 */
public record KnowledgeBase(
    UUID kbId,
    UUID ownerId,
    String name,
    String description,
    Instant createdAt,
    long pageCount,
    long documentCount
) {}
