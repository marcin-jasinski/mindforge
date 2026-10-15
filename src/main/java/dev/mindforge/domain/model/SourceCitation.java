package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A document that contributed to a page, with what its citation shows. */
public record SourceCitation(
    UUID pageId,
    UUID documentId,
    String lessonId,
    String lessonTitle,
    String sourceFilename,
    boolean conversation,
    Instant uploadedAt
) {}
