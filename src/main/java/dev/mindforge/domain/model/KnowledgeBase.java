package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A user-owned collection of documents and their derived study artifacts. */
public record KnowledgeBase(
    UUID kbId,
    UUID ownerId,
    String name,
    String description,
    Instant createdAt,
    int documentCount
) {}
