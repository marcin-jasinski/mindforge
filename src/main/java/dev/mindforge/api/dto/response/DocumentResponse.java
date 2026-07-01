package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * View of a {@link dev.mindforge.domain.model.Document} for API responses.
 * Excludes {@code originalContent} and {@code contentBlocks} (raw ingested
 * payload, not needed by clients) and never carries a password hash.
 */
public record DocumentResponse(
    UUID documentId,
    UUID knowledgeBaseId,
    String lessonId,
    String lessonTitle,
    String sourceFilename,
    String mimeType,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
