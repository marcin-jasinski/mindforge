package dev.mindforge.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

/** A {@code documents} row read without its {@code content_blocks} column, which can hold image bytes. */
public record DocumentWithoutBlocks(
    UUID documentId,
    UUID knowledgeBaseId,
    String lessonId,
    String lessonTitle,
    String contentHash,
    String sourceFilename,
    String mimeType,
    String originalContent,
    String uploadSource,
    UUID uploadedBy,
    Instant createdAt,
    Instant updatedAt
) {}
