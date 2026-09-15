package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A page revision; {@code markdownBody} is null for a tombstone. */
public record RevisionResponse(
    int revision,
    UUID ingestRunId,
    String path,
    String title,
    String description,
    String type,
    String markdownBody,
    Instant createdAt
) {}
