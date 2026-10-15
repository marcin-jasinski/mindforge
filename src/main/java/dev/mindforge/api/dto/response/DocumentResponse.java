package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * View of an uploaded document and its newest run. Excludes {@code originalContent} and {@code contentBlocks}, the
 * raw ingested payload. {@code latestRun} is null before a run exists.
 */
public record DocumentResponse(
    UUID documentId,
    UUID knowledgeBaseId,
    String lessonId,
    String lessonTitle,
    String sourceFilename,
    String mimeType,
    Instant createdAt,
    RunStateResponse latestRun
) {}
