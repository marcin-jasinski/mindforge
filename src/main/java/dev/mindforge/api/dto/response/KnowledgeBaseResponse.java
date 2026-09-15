package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** View of a knowledge base, with its counts derived at read. */
public record KnowledgeBaseResponse(
    UUID kbId,
    UUID ownerId,
    String name,
    String description,
    Instant createdAt,
    long pageCount,
    long documentCount
) {}
