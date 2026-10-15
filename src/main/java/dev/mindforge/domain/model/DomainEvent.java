package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in a knowledge base. Published through the
 * {@code EventPublisher} port within an active transaction. The sealed hierarchy
 * lets consumers exhaustively pattern-match on event type.
 */
public sealed interface DomainEvent permits DomainEvent.IngestRunQueued {

    Instant occurredAt();

    /** A {@code QUEUED} run was inserted; its listener wakes the worker after commit. */
    record IngestRunQueued(UUID runId, UUID knowledgeBaseId, Instant occurredAt) implements DomainEvent {}
}
