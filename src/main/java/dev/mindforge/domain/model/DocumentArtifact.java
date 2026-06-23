package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The accumulated study artifacts produced by the pipeline for one document:
 * summary, flashcards, concept map and quiz questions, plus the relevance check
 * and the per-step checkpoints that drive resume-on-rerun. Fields are populated
 * incrementally as steps complete, so many may be {@code null} mid-pipeline.
 */
public record DocumentArtifact(
    UUID artifactId,
    UUID documentId,
    UUID knowledgeBaseId,
    SummaryData summary,
    List<FlashcardData> flashcards,
    ConceptMapData conceptMap,
    List<String> quizQuestions,
    ValidationResult relevanceValidation,
    Map<String, StepCheckpoint> stepFingerprints,
    String completedStep,
    Instant createdAt
) {

    public DocumentArtifact {
        flashcards = flashcards == null ? List.of() : List.copyOf(flashcards);
        quizQuestions = quizQuestions == null ? List.of() : List.copyOf(quizQuestions);
        stepFingerprints = stepFingerprints == null ? Map.of() : Map.copyOf(stepFingerprints);
    }
}
