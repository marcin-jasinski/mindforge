---
id: T12
title: What of Phases 0-3 survives
type: grilling
status: open
assignee:
blocked_by: [T02, T05]
---

## Question

Phases 0–3 are built and merged: 84 Java files and Flyway migrations V1–V7. This ticket names
concretely what gets deleted, changed and kept, so the roadmap re-cut (T13) can state where
Phase 4 restarts from.

Inventory to rule on:

- **Domain records that encode the old knowledge model**: `DocumentArtifact`, `SummaryData`,
  `ConceptMapData`, `FlashcardData`, `StepCheckpoint`, `StepFingerprint`, `ContentBlock`,
  `BlockType`, `Hashes`.
- **Ports**: `ArtifactRepository`, `GraphIndexer` — do they survive, get renamed, or get
  replaced by a `WikiStore` port?
- **Persistence**: `ArtifactEntity`, `StepCheckpointEntity`, `ContentEmbeddingEntity`, their
  JPA repositories, MapStruct mappers and adapters.
- **API DTOs**: `ArtifactResponse` and `ArtifactDtoMapper` describe an artifact shape that may
  no longer exist.
- **Migrations V1–V7**, including the pgvector install. Nothing is deployed and there is no
  production data, so a squash to a new baseline is on the table and is probably cheaper than a
  migration chain that documents a model that never shipped. Say so explicitly either way —
  `docs/standards/backend/migrations.md` otherwise assumes forward-only.
- **What is untouched**: `AIGateway` and its adapter, `ModelTier`, `CostTier`, `DeadlineProfile`,
  `User`, `KnowledgeBase`, `Document`, security config, `AppProperties`. Confirm rather than
  assume — T04 may reshape `AIGateway`.

Output should be a table: file → keep / change / delete, with one-line reasons. That table is
the input to T13's phase re-cut.

## Answer
