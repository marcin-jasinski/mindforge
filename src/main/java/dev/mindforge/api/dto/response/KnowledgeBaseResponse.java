package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** View of a {@link dev.mindforge.domain.model.KnowledgeBase} for API responses. */
public record KnowledgeBaseResponse(
    UUID kbId,
    UUID ownerId,
    String name,
    String description,
    Instant createdAt,
    int documentCount
) {}
