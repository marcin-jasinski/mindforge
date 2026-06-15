# Neo4j is a derived read projection, not a source of truth

MindForge operates two databases: PostgreSQL (source of truth for all business data)
and Neo4j (a derived, read-optimised projection of concept relationships). Neo4j is
populated exclusively by consuming outbox events emitted when a `ConceptMap` artifact
is persisted to PostgreSQL. It is never written to directly from application logic, and
it is never read from during writes. The entire Neo4j graph can be discarded and fully
rebuilt from PostgreSQL at any time.

## Considered Options

- **Neo4j as primary store for concepts** — rejected: this would require cross-store
  write transactions (PostgreSQL + Neo4j in the same commit) or eventual consistency
  management between two sources of truth. Either approach introduces failure modes
  that are hard to reason about. The graph has no data that is not already derivable
  from the canonical PostgreSQL artifacts.

- **Graph relationships in PostgreSQL only (adjacency list / recursive CTE)** —
  rejected: `WITH RECURSIVE` queries for multi-hop concept neighbourhood traversal are
  significantly more expensive and harder to write than Cypher. Native graph traversal
  is Neo4j's primary strength and the main reason to include it.

- **Neo4j as derived projection (selected)** — the outbox pattern ensures at-least-once
  delivery of `ConceptMapCreated` and `ConceptMapUpdated` events to the Neo4j adapter.
  A `backfill` CLI command (`mindforge-backfill`) rebuilds the full graph from
  PostgreSQL artifacts when needed.

## Consequences

- No distributed transaction or saga is needed between PostgreSQL and Neo4j. If the
  Neo4j adapter is unavailable, events queue in the outbox and are processed when it
  recovers.
- Concept neighbourhood queries (e.g., "which concepts are adjacent to X within 2 hops?")
  use Cypher via Spring Data Neo4j repositories. All writes go to PostgreSQL only.
- Neo4j can be disabled entirely (e.g., in CI tests) without affecting the ingestion
  pipeline or quiz functionality. The concept graph view degrades gracefully.
- The outbox table in PostgreSQL is the integration contract; its schema is versioned
  by Flyway and must not be changed without a migration.
