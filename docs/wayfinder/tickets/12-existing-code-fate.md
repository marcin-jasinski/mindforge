---
id: T12
title: What of Phases 0-3 survives
type: grilling
status: closed
assignee: claude
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


**Inherited from T02** (the knowledge-model rows only — the rest of the table is still this ticket's).
Ruled dead: `DocumentArtifact`, `SummaryData`, `ConceptMapData`. Ruled surviving: `Document`,
`ContentBlock`, `BlockType`, `ContentHash`, `Hashes`, `LessonIdentity`. `ArtifactRepository` is
superseded by the `WikiStore` port. `StepCheckpoint` / `StepFingerprint` await T07 and
`FlashcardData` awaits T08, so neither is ruled on yet.

**Inherited from T04.** Adds to the inventory.

- **Delete**: `Agent`, `AgentContext`, `AgentResult`, `AgentCapability` (Phase 1), plus the planned
  `AgentRegistry` and `OrchestrationGraph` from unbuilt Phase 5.
- **Keep untouched**: `AIGateway`, `AIGatewayAdapter`, `CompletionResult`, `DeadlineProfile`, `CostTier`,
  `ModelTier`, `ProcessingSettings` — keeping every call single-shot is what preserves them.
- **Keep**: `ParserRegistry`'s MIME dispatch, the one genuinely open extension point. CLAUDE.md's
  open/closed rule was doing real work for parsers and imagined work for agents; the rule needs rewording,
  not dropping.

**Inherited from T05.** `StoragePort` is not built and the wiki does not need it; uploaded sources already
live in `documents.original_content` / `content_blocks`. Whether binary uploads still need it is this
ticket's call. `ArtifactRepository` → replaced by `WikiStore` (one JPA adapter). New tables: `wiki_pages`,
`page_links`, `page_revisions`, `page_sources`, `page_supersessions` (plus T07's `ingest_runs`). No pending
or approval tables.

**Inherited from T06.** `LessonIdentity` → **change**: its `slugify` maps "Mitoza komórkowa" to
`mitoza-kom-rkowa`; it must share the wiki's transliterating `slugify` (NFD, strip marks, `ł→l` etc.) and reserve
`log` alongside `index`. `WikiPage`'s `slug` is renamed `path` and gains `description`.

**Inherited from T07.** Rulings for the table:

- **Delete**: `StepCheckpoint`, `StepFingerprint`, `StepCheckpointEntity`, `StepCheckpointJpaRepository`, V5
  `step_checkpoints`, `DocumentStatus` and `documents.status`.
- **Keep**: `Hashes`, `ContentHash`.
- **Change**:
  - `DomainEvent` is re-cut around runs (`IngestStepCompleted`, `IngestRunCompleted`, `IngestRunFailed`; T09
    owns `GraphProjectionUpdated`).
  - `UploadSource` gains `CONVERSATION`.
  - `DocumentRepository.findByContentHash` must take `kbId` — today it deduplicates across tenants, which is a
    security bug.
  - V3's `UNIQUE (knowledge_base_id, lesson_id)` becomes a partial `UNIQUE (knowledge_base_id, content_hash)`.
  - `knowledge_bases` gains `active_run_id`.
- **New**: the `ingest_runs` table.

**Inherited from T08.**

- **Change**: `FlashcardData` → `Flashcard`. `pageId` replaces `lessonId` in `computeCardId`, and the record adds
  `pageId`, `sectionAnchor` and `generatedAtRevision`.
- **Keep**: `CardType`.
- **New tables**: `flashcards`, `study_events`, `quiz_sessions`.
- `ArtifactRepository.countFlashcards` dies with its port.

**Inherited from T09–T10.**

- **Delete**: V6 (`vector` extension) and V7 (`content_embeddings`), `ContentEmbeddingEntity`, the `GraphIndexer`
  port, `ConceptMapData`, `DomainEvent.GraphProjectionUpdated`. Also the Neo4j and pgvector wiring in `pom.xml`,
  `application*.yml`, `AiConfig` / `AIGatewayAdapter` (any embedding-model bean) and `TestContainerBase` (the Neo4j
  container) — each of those files references Neo4j, pgvector or embeddings today.
- **Change**: `ingest_runs.kind` includes `LINT`, and `ingest_runs` gains `findings JSONB`. No Lint code exists yet.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**Phase 4 restarts after a short cleanup phase — "Phase 3b — Wiki pivot cleanup", shaped like Phase 2b.** It deletes
22 of the 72 main-source files and 4 of the 9 test classes (12 test files counting `support/`; counts corrected by
[T28](28-minor-review-findings.md)), changes 14 files, squashes V1–V7 into a new V1 baseline,
and keeps everything else. **The one ruling that contradicts an earlier ticket's assumption:** `AIGateway` is *not*
untouched. It still declares `float[] embed(String)`, and T09 removed its only consumer.

**The sorting rule.** *Delete what encodes a dead design. Change what is wrong now. Keep what the new design uses
unchanged — even if its first consumer is a later phase.* A file whose shape is right but whose consumer is not built
yet (`ValidationResult`, `CardType`) stays. A file whose shape is wrong (`FlashcardData`'s lesson-keyed id) goes now,
and its successor is written in the phase that uses it. Rows marked *later* change in a named phase, not in 3b.

### Main source (`src/main/java/dev/mindforge`)

| File | Ruling | Reason |
|---|---|---|
| `domain/model/Agent`, `AgentCapability`, `AgentContext`, `AgentResult` | **delete** | Agent abstraction deleted (T04) |
| `domain/model/DocumentArtifact`, `SummaryData`, `ConceptMapData` | **delete** | Old knowledge model (T02); concept map is `page_links` (T09) |
| `domain/model/StepCheckpoint`, `StepFingerprint` | **delete** | A re-run is a new run; nothing is skipped (T07) |
| `domain/model/DocumentStatus` | **delete** | A document's state is its latest run (T07) |
| `domain/model/FlashcardData` | **delete** | Id keyed on `lessonId` is the dead design; Phase 10 writes `Flashcard` keyed on `pageId` (T08). *Sequencing amendment to T08's "change": same end state, no dead record across Phases 4–9.* |
| `domain/model/CardType` | keep | Unchanged in T08; first consumer Phase 10 |
| `domain/model/ValidationResult` | keep | RelevanceGuard still returns it (T04) |
| `domain/model/ContentBlock`, `BlockType`, `ContentHash`, `Hashes` | keep | T02, T07 |
| `domain/model/CompletionResult`, `CostTier`, `DeadlineProfile`, `DeadlineExceededException`, `ModelTier`, `AIGatewayUnavailableException` | keep | Every call stays single-shot (T04) |
| `domain/model/ProcessingSettings` | keep; *later* | Chunking still feeds Extract on long documents. Gains the claim cap (Phase 5) and `newPagesPerSession` (Phase 10) |
| `domain/model/User` | keep | — |
| `domain/model/KnowledgeBase` | keep; *later* | Gains a derived `pageCount` in Phase 5 (T02), computed at read, not a stored counter |
| `domain/model/UploadSource` | keep; *later* | Gains `CONVERSATION` with conversation edits (T03, T07) |
| `domain/model/Document` | **change** | Drop `status` (T07) |
| `domain/model/DomainEvent` | **change** | Delete `PipelineStepCompleted`, `ProcessingCompleted` (carries `DocumentArtifact`), `ProcessingFailed` and `GraphProjectionUpdated`; keep `DocumentIngested`. Run events are added in Phase 5 (T07) |
| `domain/model/LessonIdentity`, `LessonIdentityException` | **change** | `slugify` mangles Polish (`mitoza-kom-rkowa`) — adopt the transliterating slug; reserve `log` as well as `index` (T06) |
| `domain/port/AIGateway` | **change** | Delete `embed` — no embeddings anywhere (T09). `complete` untouched |
| `domain/port/ArtifactRepository` | **delete** | Replaced by `WikiStore`, written in Phase 5 (T05) |
| `domain/port/GraphIndexer` | **delete** | No Neo4j (T09) |
| `domain/port/DocumentRepository` | **change** | `findByContentHash` takes `kbId` (**cross-tenant bug today**, T07); delete `updateStatus` |
| `domain/port/EventPublisher` | keep | Its contract — publish inside the caller's transaction — is exactly T07's rule |
| `infrastructure/ai/AIGatewayAdapter` | **change** | Drop `EmbeddingModel` and `embed` |
| `infrastructure/config/AiConfig` | **change** | Drop the `EmbeddingModel` parameter |
| `infrastructure/config/PersistenceConfig` | **change** | Drop the `ArtifactRepository` bean |
| `infrastructure/config/AppConfig`, `AppProperties`, `OpenApiConfig` | keep | — |
| `infrastructure/persistence/entity/ArtifactEntity`, `StepCheckpointEntity`, `ContentEmbeddingEntity` | **delete** | T02, T07, T09 |
| `infrastructure/persistence/jpa/ArtifactJpaRepository`, `StepCheckpointJpaRepository` | **delete** | Their tables are gone |
| `infrastructure/persistence/mapper/ArtifactEntityMapper` | **delete** | — |
| `infrastructure/persistence/adapter/ArtifactRepositoryAdapter` | **delete** | — |
| `infrastructure/persistence/entity/DocumentEntity` | **change** | Drop `status` |
| `infrastructure/persistence/jpa/DocumentJpaRepository`, `adapter/DocumentRepositoryAdapter`, `mapper/DocumentEntityMapper` | **change** | Hash lookup scoped by `knowledgeBaseId`; no status |
| `infrastructure/persistence/entity/BaseEntity`, `KnowledgeBaseEntity`, `UserEntity` + their JPA repositories and mappers | keep | `active_run_id` joins `KnowledgeBaseEntity` in Phase 5, with `ingest_runs` |
| `api/dto/response/ArtifactResponse`, `api/mapper/ArtifactDtoMapper` | **delete** | Artifact shape no longer exists |
| `api/dto/response/DocumentResponse`, `api/mapper/DocumentDtoMapper` | **change** | Drop `status`; Phase 9 adds a run-status view when the endpoint exists |
| `api/dto/request/*`, `KnowledgeBaseResponse`, `UserResponse`, `KnowledgeBaseDtoMapper`, `UserDtoMapper` | keep | — |
| `MindForgeApplication` | keep | — |

### Tests (`src/test/java/dev/mindforge`)

| File | Ruling | Reason |
|---|---|---|
| `unit/domain/AgentResultTest`, `StepFingerprintTest`, `FlashcardDataTest` | **delete** | Their subjects are deleted |
| `integration/persistence/ArtifactRepositoryAdapterTest` | **delete** | — |
| `integration/graph/.gitkeep` | **delete** | No graph store |
| `integration/persistence/DocumentRepositoryAdapterTest` | **change** | No status; **add a cross-knowledge-base dedup test** — same hash in two KBs returns nothing across them |
| `unit/domain/LessonIdentityTest` | **change** | Polish transliteration cases; `log` reserved |
| `unit/domain/ContentHashTest` | keep | — |
| `support/StubAIGateway`, `unit/domain/StubAIGatewayTest`, `unit/infrastructure/ai/AIGatewayAdapterTest` | **change** | Drop `embed` and the two embedding tests |
| `support/TestContainerBase` | **change** | Drop the Neo4j container; `pgvector/pgvector:pg15` → plain `postgres:15` |
| `support/TestFixtures` | **change** | Drop `makeDocumentArtifact` and the `status` parameter |
| `unit/agent/.gitkeep` | keep | `dev.mindforge.agent` stays as the home of the concrete LLM services (see below) |

### Resources and build

| File | Ruling | Reason |
|---|---|---|
| `db/migration/V1`–`V7` | **squash** → new `V1__baseline.sql` | See decision below |
| `application.yml` | **change** | Delete `spring.neo4j.*` and `spring.ai.vectorstore.*` |
| `application-dev.yml` | **change** | Delete `spring.neo4j.*` |
| `pom.xml` | **change** | Delete `spring-boot-starter-data-neo4j`, `spring-ai-starter-vector-store-pgvector`, `testcontainers-neo4j` |
| `env.example` | **change** | Delete the `NEO4J_*` block |

### Three rulings beyond the table

**1. Squash V1–V7 into one baseline — once, now, and say so.** Nothing is deployed and no environment holds data worth
keeping. A chain that creates `artifacts`, `step_checkpoints`, the `vector` extension and `content_embeddings`, only to
drop them in V8–V11, documents a model that never shipped — and would teach every future reader the wrong schema first.
The new `V1__baseline.sql` contains only the surviving tables in their corrected shape:

- `users` — unchanged;
- `knowledge_bases` — unchanged; `active_run_id` arrives with `ingest_runs`;
- `documents` — no `status`; `UNIQUE (knowledge_base_id, lesson_id)` and the global `content_hash` index replaced by
  `UNIQUE (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION'`, with a plain index on
  `(knowledge_base_id, lesson_id)`.

The wiki tables, `ingest_runs` and the study tables are **not** pre-created: each lands as a forward migration in the
phase that uses it (minimal-implementation standard — no future stubs).

`migrations.md`'s forward-only rule stands and is not weakened. It is about deployed migrations, and these are not
deployed. The squash is recorded as a one-time exception, dated. **Cost:** every developer's local database fails
Flyway checksum validation after the squash and must be dropped and recreated — a one-line note in the Phase 3b
checklist.

**2. `dev.mindforge.agent` keeps its name and its layer rule.** The concrete LLM services T04, T08 and T10 named — claim
extractor, page writer, supersession detector, link checker, preprocessor, relevance guard, flashcard generator, quiz
generator, quiz evaluator — each call `AIGateway` and load prompt files. That is exactly what CLAUDE.md's `agent` row
already permits (domain + `infrastructure.ai.*`). Only the *interface* died, not the package; renaming a layer for
vocabulary's sake is churn. The package means "services that call a model", not "implementations of `Agent`".

**3. Where Phase 4 restarts.** Phase 3b executes every **delete** / **change** / **squash** row above and ends green
(`mvn verify`). Phase 4 then begins exactly as planned for parsing and upload, with its 4.5 `IngestionService`
re-cut by T07. Nothing in Phase 3b builds new behaviour.

### Feeds

- **T13** — add Phase 3b to the implementation plan and roadmap using this table. `migrations.md` gains a dated note
  recording the one-time pre-deployment squash. CLAUDE.md's `agent` row is reworded to "services that call a model"
  (no `Agent` interface). `docs/project/architecture.md` must stop describing `AIGateway.embed`.
