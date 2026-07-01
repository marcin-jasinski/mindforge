package dev.mindforge.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.ConceptMapData;
import dev.mindforge.domain.model.FlashcardData;

/**
 * View of a {@link dev.mindforge.domain.model.DocumentArtifact} for API responses.
 * Excludes {@code stepFingerprints} (internal pipeline resume state). Fields
 * forbidden by {@code docs/standards/security/web-security.md} (reference
 * answer, grounding context, cost) don't exist on the domain model yet — this
 * mapper must not gain them if a future quiz-grading feature adds them there.
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
