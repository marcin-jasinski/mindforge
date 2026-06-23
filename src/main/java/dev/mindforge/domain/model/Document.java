package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An uploaded source document and its parsed content blocks. Source of truth for
 * the ingestion pipeline. Immutable; {@code contentBlocks} is defensively copied.
 */
public record Document(
    UUID documentId,
    UUID knowledgeBaseId,
    LessonIdentity lessonIdentity,
    ContentHash contentHash,
    String sourceFilename,
    String mimeType,
    String originalContent,
    List<ContentBlock> contentBlocks,
    UploadSource uploadSource,
    UUID uploadedBy,
    DocumentStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public Document {
        contentBlocks = contentBlocks == null ? List.of() : List.copyOf(contentBlocks);
    }
}
