package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A live page, {@code markdown} rendered with its supersession notes and citations. */
public record PageResponse(
    UUID pageId,
    String path,
    String title,
    String description,
    String type,
    int revision,
    String markdown,
    Instant createdAt,
    Instant updatedAt
) {}
