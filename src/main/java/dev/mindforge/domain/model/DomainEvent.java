package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in a knowledge base. Published through the
 * {@code EventPublisher} port within an active transaction. The sealed hierarchy
 * lets consumers exhaustively pattern-match on event type.
 */
public sealed interface DomainEvent permits DomainEvent.DocumentIngested {

    Instant occurredAt();

    /** A new document was accepted for ingest. */
    record DocumentIngested(
        UUID documentId,
        UUID knowledgeBaseId,
        String lessonId,
        ContentHash contentHash,
        Instant occurredAt
    ) implements DomainEvent {}
}
