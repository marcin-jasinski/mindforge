package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.ConceptMapData;
import dev.mindforge.domain.model.FlashcardData;

/**
 * View of a {@link dev.mindforge.domain.model.DocumentArtifact} for API responses.
 * Excludes {@code stepFingerprints} (internal pipeline resume state) and any
 * reference-answer, grounding, or cost fields per
 * {@code docs/standards/security/web-security.md}.
 */
public record ArtifactResponse(
    UUID artifactId,
    UUID documentId,
    UUID knowledgeBaseId,
    String summary,
    List<String> keyPoints,
    List<FlashcardData> flashcards,
    ConceptMapData conceptMap,
    List<String> quizQuestions,
    String completedStep,
    Instant createdAt
) {}
