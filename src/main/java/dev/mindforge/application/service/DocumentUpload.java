package dev.mindforge.application.service;

import java.util.UUID;

import dev.mindforge.domain.model.UploadSource;

/**
 * One upload into a knowledge base, as received from any channel. {@code lessonId} optionally overrides
 * the resolved lesson id; {@code newVersion} says the upload is a new version of an existing lesson.
 */
public record DocumentUpload(
    UUID knowledgeBaseId,
    UUID uploadedBy,
    UploadSource source,
    String filename,
    String mimeType,
    byte[] content,
    String lessonId,
    boolean newVersion
) {}
