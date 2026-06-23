package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for everything that happens in the processing pipeline. Published through
 * the {@code EventPublisher} port within an active transaction. The sealed
 * hierarchy lets consumers exhaustively pattern-match on event type.
 */
public sealed interface DomainEvent
    permits DomainEvent.DocumentIngested,
            DomainEvent.PipelineStepCompleted,
            DomainEvent.ProcessingCompleted,
            DomainEvent.ProcessingFailed,
            DomainEvent.GraphProjectionUpdated {

    UUID documentId();

    Instant occurredAt();

    /** A new document was accepted for processing. */
    record DocumentIngested(
        UUID documentId,
        UUID knowledgeBaseId,
        String lessonId,
        ContentHash contentHash,
        Instant occurredAt
    ) implements DomainEvent {}

    /** A single pipeline step finished and its checkpoint was saved. */
    record PipelineStepCompleted(
        UUID documentId,
        String stepName,
        String outputKey,
        Instant occurredAt
    ) implements DomainEvent {}

    /** All pipeline steps completed successfully; the artifact is final. */
    record ProcessingCompleted(
        UUID documentId,
        UUID knowledgeBaseId,
        DocumentArtifact artifact,
        Instant occurredAt
    ) implements DomainEvent {}

    /** Processing stopped before completion. {@code reason} is user-surfacable. */
    record ProcessingFailed(
        UUID documentId,
        String stepName,
        String reason,
        boolean retryable,
        Instant occurredAt
    ) implements DomainEvent {}

    /** The Neo4j concept projection was (re)built for a document's lesson. */
    record GraphProjectionUpdated(
        UUID documentId,
        UUID knowledgeBaseId,
        String lessonId,
        int nodeCount,
        int edgeCount,
        Instant occurredAt
    ) implements DomainEvent {}
}
