# MindForge — Implementation Plan

> **Version:** 3.1 — wiki re-cut, with the 2026-09-12 spec review folded in
> **Date:** 2026-09-12
> **Status:** Active
> **Reference:** [architecture.md](./architecture.md) · [CONTEXT.md](../../CONTEXT.md) · [wiki re-cut map](../wayfinder/okf-wiki-map.md) · ADRs 0010–0018

---

## Table of Contents

**Core System (Phases 0–13)**

1. [Phase 0 — Project Scaffolding and Tooling](#phase-0--project-scaffolding-and-tooling)
2. [Phase 1 — Domain Layer](#phase-1--domain-layer)
3. [Phase 2 — Infrastructure Foundation](#phase-2--infrastructure-foundation)
4. [Phase 3 — AI Gateway](#phase-3--ai-gateway)
5. [Phase 3b — Wiki Pivot Cleanup](#phase-3b--wiki-pivot-cleanup) *(new)*
6. [Phase 4 — Document Parsing and Ingestion](#phase-4--document-parsing-and-ingestion) *(changed)*
7. [Phase 5 — Wiki Domain and Store](#phase-5--wiki-domain-and-store) *(re-cut)*
8. [Phase 6 — Ingest Pipeline](#phase-6--ingest-pipeline) *(re-cut)*
9. [Phase 7 — Lint](#phase-7--lint) *(number reused)*
10. [Phase 8 — Event System](#phase-8--event-system-retired-in-v31) *(retired in v3.1)*
11. [Phase 9 — API Layer (Spring MVC)](#phase-9--api-layer-spring-mvc) *(changed)*
12. [Phase 9b — Bundle Export](#phase-9b--bundle-export) *(new)*
13. [Phase 10 — Quiz and Flashcard Services](#phase-10--quiz-and-flashcard-services) *(changed)*
14. [Phase 11 — Query](#phase-11--query) *(re-cut)*
15. [Phase 12 — Angular Frontend](#phase-12--angular-frontend) *(changed)*
16. [Phase 13 — Docker and Deployment](#phase-13--docker-and-deployment) *(changed)*

**Post-MVP Enhancements (Phases 14–21)**

17. [Phase 14 — Observability and Tracing](#phase-14--observability-and-tracing) *(changed)*
18. [Phase 15 — CLI Entry Points](#phase-15--cli-entry-points) *(changed)*
19. [Phase 16 — Image Analysis](#phase-16--image-analysis) *(changed)*
20. [Phase 17 — Article Fetcher](#phase-17--article-fetcher) *(changed)*
21. [Phase 18 — Discord Bot](#phase-18--discord-bot) *(changed)*
22. [Phase 19 — Slack Bot](#phase-19--slack-bot) *(changed)*
23. [Phase 20 — Security Hardening](#phase-20--security-hardening)
24. [Phase 21 — End-to-End Testing and CI/CD](#phase-21--end-to-end-testing-and-cicd) *(changed)*

25. [Dependency Graph](#dependency-graph)

---

## Overview

This plan decomposes MindForge into sequential phases. Phases 0–13 deliver the core
learning system: documents ingested into a per-knowledge-base wiki that compounds, Lint,
OKF bundle export, flashcards and quizzes cut from the wiki, Query, the Angular SPA and
Docker deployment. Phases 14–21 layer in observability, extended sources, delivery channels
and quality gates after the core system is running.

**Version 3.0 is the wiki re-cut.** Phases 0–3 are recorded as they were built. Phase 3b
then removes or changes the parts of them that encoded the old per-document artifact model.
Phase numbering is stable where a phase's purpose survived, and each phase heading says
plainly when it was changed, re-cut, reused or added. The reasoning for every change lives in
[`docs/wayfinder/okf-wiki-map.md`](../wayfinder/okf-wiki-map.md) and ADRs 0010–0018; use the
vocabulary in [`CONTEXT.md`](../../CONTEXT.md).

**Version 3.1 folds in the 2026-09-12 spec review** (map tickets T14–T29). No phase is added; Phase 8 is retired
into Phases 6 and 9; the tasks of 3b–7, 9–14, 17 and 21 state the review's answers and cite the tickets behind
them, and the most-changed phases (3b, 4, 6, 7, 9, 9b) say so in their headings.

Each phase is self-contained and produces verifiable deliverables. Phases must be completed
in order because later phases depend on the artifacts of earlier ones.

**Conventions used in this document:**

- `[ ]` — task or phase not started
- `[x]` — task or phase completed
- Each phase has a completion checklist. A phase is DONE when every task and subtask is `[x]`.
- Code references use Java package paths under `src/main/java/dev/mindforge/`.

---

## [x] Phase 0 — Project Scaffolding and Tooling

**Goal:** Establish the Maven project skeleton, Spring Boot bootstrap, configuration
loading via `@ConfigurationProperties`, developer environment, and CI prerequisites.

### Tasks

- [x] **0.1 — Create `pom.xml` with Maven project metadata**
  - Group: `dev.mindforge`, artifact: `mindforge`, Java source: `21`.
  - Declare Spring Boot parent BOM (`spring-boot-starter-parent 4.1`).
  - Include all runtime dependencies: `spring-boot-starter-web`,
    `spring-boot-starter-data-jpa`, `spring-boot-starter-data-neo4j`,
    `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`,
    `spring-ai-openai-spring-boot-starter`, `spring-ai-pgvector-store-spring-boot-starter`,
    `flyway-core`, `caffeine`, `jackson-databind`, `pdfbox`, `apache-poi`,
    `jjwt-api`, `jjwt-impl`.
  - Include test scope: `spring-boot-starter-test`, `mockito-core`, `testcontainers`,
    `testcontainers-postgresql`, `testcontainers-neo4j`, `assertj-core`.
  - Configure `frontend-maven-plugin` to integrate Angular build into Maven lifecycle.
  - Add `jacoco-maven-plugin` with minimum 70% instruction coverage gate.

- [x] **0.2 — Create the package directory tree**
  - Scaffold all Java source directories under `src/main/java/dev/mindforge/`:
    `domain/model/`, `domain/port/`,
    `application/service/`,
    `infrastructure/persistence/`, `infrastructure/graph/`, `infrastructure/ai/`,
    `infrastructure/parsing/`, `infrastructure/cache/`, `infrastructure/storage/`,
    `infrastructure/security/`, `infrastructure/event/`,
    `agent/`,
    `api/controller/`, `api/dto/`, `api/config/`.
  - Scaffold test directories under `src/test/java/dev/mindforge/`:
    `unit/domain/`, `unit/application/`, `unit/agent/`,
    `integration/persistence/`, `integration/graph/`, `integration/api/`.
  - Scaffold `src/main/resources/`: `application.yml`, `application-dev.yml`, `prompts/pl/`.
  - Scaffold `src/main/resources/db/migration/` (Flyway SQL migrations — prefix `V`).
  - Scaffold `frontend/` (Angular project created in Phase 12).

- [x] **0.3 — Create `env.example`**
  - Include every environment variable the app needs, with comments indicating required vs. optional.
  - Must cover: `DATABASE_URL`, `NEO4J_URI`, `OPENROUTER_API_KEY`, `JWT_SECRET`,
    `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`.

- [x] **0.4 — Create `.gitignore`**
  - Java: `target/`, `*.class`, `*.jar`.
  - Node: `node_modules/`, `frontend/dist/`.
  - IDE: `.vscode/`, `.idea/`.
  - Environment: `.env` (not `env.example`).

- [x] **0.5 — Verify Maven build**
  - `mvn compile` succeeds in a clean checkout.
  - All Spring Boot auto-configuration resolves without errors.
  - No static-init side effects at class load time.

- [x] **0.6 — Scaffold test utilities**
  - Create `StubAIGateway` (implements `AIGateway`; returns configurable canned responses).
  - Create `make*` static factory methods for future domain objects (bodies return `null`; filled in per phase).
  - Create `@Testcontainers` base configuration (`PostgreSQLContainer`, `Neo4jContainer`).

### Completion Checklist

- [x] `mvn compile` succeeds on a clean checkout.
- [x] `mvn test` runs with zero tests and zero errors.
- [x] `env.example` documents all required environment variables.
- [x] Package directory tree matches architecture Section 5.

---

## [x] Phase 1 — Domain Layer

**Goal:** Implement the pure Java domain layer (`dev.mindforge.domain`) with zero I/O and
zero framework imports. Only the types needed for Phases 2–6 are defined here; types that
serve later phases are added when those phases begin.

### Tasks

- [x] **1.1 — Core enums** (`dev.mindforge.domain.model`)
  - `DocumentStatus`: `PENDING`, `PROCESSING`, `DONE`, `FAILED`
  - `UploadSource`: `API`, `FILE_WATCHER` (delivery channels added in Phase 18–19)
  - `BlockType`: `TEXT`, `IMAGE`, `CODE`, `AUDIO`, `VIDEO`
  - `CardType`: `BASIC`, `CLOZE`, `REVERSE`
  - `ModelTier`: `SMALL`, `LARGE`, `VISION`
  - `CostTier`: `LOW`, `MEDIUM`, `HIGH`
  - `DeadlineProfile`: `INTERACTIVE`, `BATCH`, `BACKGROUND`

- [x] **1.2 — Value objects**
  - [x] 1.2.1 — `ContentHash` record: `String sha256`. Static `compute(byte[] raw)` method
    using `MessageDigest.getInstance("SHA-256")`. Immutable.
  - [x] 1.2.2 — `LessonIdentity` record: `String lessonId`, `String title`. Static
    `resolve(Map<String, String> metadata, String filename)` implementing the five-step
    deterministic resolution algorithm (architecture Section 6.2): frontmatter `lesson_id` →
    frontmatter `title` (slugified) → PDF metadata `Title` → filename stem. Validation:
    max 80 chars, `[a-z0-9\-_]` only, not empty, not in reserved names (`index`, `default`).
    Throws `LessonIdentityException` on failure.

- [x] **1.3 — Core entities**
  - [x] 1.3.1 — `ContentBlock` record: `BlockType blockType`, `String content`,
    `String mediaRef`, `String mediaType`, `Map<String, Object> metadata`, `int position`.
  - [x] 1.3.2 — `Document` record: `UUID documentId`, `UUID knowledgeBaseId`,
    `LessonIdentity lessonIdentity`, `ContentHash contentHash`, `String sourceFilename`,
    `String mimeType`, `String originalContent`, `List<ContentBlock> contentBlocks`,
    `UploadSource uploadSource`, `UUID uploadedBy`, `DocumentStatus status`,
    `Instant createdAt`, `Instant updatedAt`.
  - [x] 1.3.3 — `KnowledgeBase` record: `UUID kbId`, `UUID ownerId`, `String name`,
    `String description`, `Instant createdAt`, `int documentCount`.
  - [x] 1.3.4 — `User` record: `UUID userId`, `String displayName`, `String email`,
    `String passwordHash`, `String avatarUrl`, `Instant createdAt`, `Instant lastLoginAt`.

- [x] **1.4 — Pipeline and artifact types**
  - [x] 1.4.1 — `StepFingerprint` record: `String inputHash`, `String promptVersion`,
    `String modelId`, `String agentVersion`. Static `compute(...)` returns
    `sha256("inputHash|promptVersion|modelId|agentVersion")[:16]`.
  - [x] 1.4.2 — `StepCheckpoint` record: `String outputKey`, `String fingerprint`,
    `Instant completedAt`.
  - [x] 1.4.3 — `SummaryData` record: `String summary`, `List<String> keyPoints`.
  - [x] 1.4.4 — `FlashcardData` record: `String cardId`, `CardType cardType`,
    `String front`, `String back`. Deterministic `cardId` via
    `sha256("kbId|lessonId|cardType|front|back")[:16]`.
  - [x] 1.4.5 — `ConceptMapData` record: `List<ConceptNode> nodes`,
    `List<ConceptEdge> edges`. Inner records `ConceptNode` and `ConceptEdge`.
  - [x] 1.4.6 — `ValidationResult` record: `boolean passed`, `String reason`,
    `float confidence`.
  - [x] 1.4.7 — `DocumentArtifact` record: `UUID artifactId`, `UUID documentId`,
    `UUID knowledgeBaseId`, `SummaryData summary`, `List<FlashcardData> flashcards`,
    `ConceptMapData conceptMap`, `List<String> quizQuestions`,
    `ValidationResult relevanceValidation`, `Map<String, StepCheckpoint> stepFingerprints`,
    `String completedStep`, `Instant createdAt`.
  - [x] 1.4.8 — `CompletionResult` record: `String content`, `int inputTokens`,
    `int outputTokens`, `String model`, `String provider`, `long latencyMs`,
    `double costUsd`.

- [x] **1.5 — Domain events** (sealed interface hierarchy)
  - `sealed interface DomainEvent permits DocumentIngested, PipelineStepCompleted,
    ProcessingCompleted, ProcessingFailed, GraphProjectionUpdated`
  - Each event is a `record` implementing `DomainEvent`, carrying the fields from architecture Section 6.3.

- [x] **1.6 — Agent interface and context types**
  - [x] 1.6.1 — `Agent` interface: `String name()`, `AgentCapability capability()`,
    `AgentResult execute(AgentContext context)`.
  - [x] 1.6.2 — `AgentCapability` record: `String name`, `String description`,
    `ModelTier requiredModelTier`, `CostTier estimatedCostTier`.
  - [x] 1.6.3 — `AgentContext` record: `UUID documentId`, `UUID knowledgeBaseId`,
    `DocumentArtifact artifact`, `AIGateway gateway`, `ProcessingSettings settings`.
  - [x] 1.6.4 — `AgentResult` sealed interface: `record Success(String outputKey,
    int tokensUsed, double costUsd, long durationMs) implements AgentResult`,
    `record Failure(String error, boolean retryable) implements AgentResult`.
  - [x] 1.6.5 — `ProcessingSettings` record: chunk size, overlap, feature flags,
    model-tier mappings.

- [x] **1.7 — Port interfaces** (`dev.mindforge.domain.port`)
  - `DocumentRepository`: `save`, `findById`, `findByContentHash`, `updateStatus`,
    `listByKnowledgeBase`.
  - `ArtifactRepository`: `saveCheckpoint`, `loadLatest`, `countFlashcards`.
  - `AIGateway`: `complete(ModelTier tier, String prompt, DeadlineProfile deadline)`,
    `embed(String text)`.
  - `GraphIndexer`: `indexArtifact(DocumentArtifact artifact)`, `removeByLesson(UUID kbId, String lessonId)`.
  - `EventPublisher`: `publish(DomainEvent event)` — called within an active transaction.

- [x] **1.8 — Unit tests for domain layer**
  - `LessonIdentity.resolve()`: all five resolution steps, validation rules, reserved name rejection.
  - `ContentHash.compute()`: determinism, different inputs → different hashes.
  - `FlashcardData.cardId`: same inputs → same ID; different `kbId` → different ID.
  - `StepFingerprint.compute()`: same inputs → same hash; any input change → different hash.
  - `AgentResult` pattern matching via `sealed` hierarchy.

### Completion Checklist

- [x] Zero framework imports anywhere in `dev.mindforge.domain`.
- [x] All domain types are `record` or `interface` — no mutable state.
- [x] `mvn test -Dtest="**/unit/domain/**"` passes.
- [x] `StepFingerprint` and `LessonIdentity` have full validation coverage.

---

## [x] Phase 2 — Infrastructure Foundation

**Goal:** Implement Spring Boot configuration, PostgreSQL schema via Flyway migrations,
JPA entities, and all Spring Data JPA repository adapters.

### Tasks

- [x] **2.1 — Configuration** (`dev.mindforge.infrastructure.config`)
  - `AppProperties` class annotated `@ConfigurationProperties(prefix = "mindforge")`.
  - Fields: `ai` (OpenRouter URL, key), `security` (JWT secret, expiry), `db` (datasource).
  - Validated via `@Validated` + JSR-380 annotations on all required fields.

- [x] **2.2 — Flyway migrations** (`src/main/resources/db/migration/`)
  - `V1__create_users.sql`
  - `V2__create_knowledge_bases.sql`
  - `V3__create_documents.sql`
  - `V4__create_artifacts.sql`
  - `V5__create_step_checkpoints.sql`
  - `V6__create_vector_extension.sql` — `CREATE EXTENSION IF NOT EXISTS vector`
  - `V7__create_embeddings.sql` — `content_embeddings` table with `vector(1536)` column
  - Each migration is irreversible; never use `DROP` without a compensating up-migration.

- [x] **2.3 — JPA entities** (`dev.mindforge.infrastructure.persistence.entity`)
  - `DocumentEntity`, `KnowledgeBaseEntity`, `UserEntity`, `ArtifactEntity`,
    `StepCheckpointEntity`, `ContentEmbeddingEntity`.
  - Use `@MappedSuperclass` `BaseEntity` with `@CreatedDate`, `@LastModifiedDate`.
  - Bidirectional mapping only where queries require it.

- [x] **2.4 — Repository adapters** (`dev.mindforge.infrastructure.persistence`)
  - `DocumentRepositoryAdapter implements DocumentRepository` — wraps `DocumentJpaRepository`.
  - `ArtifactRepositoryAdapter implements ArtifactRepository` — wraps `ArtifactJpaRepository`.
  - All adapters translate between JPA entities and domain records.

- [x] **2.5 — Integration tests**
  - `@Testcontainers` with real PostgreSQL 15.
  - `DocumentRepositoryAdapterTest`: save, findById, findByContentHash, updateStatus, deduplication.
  - `ArtifactRepositoryAdapterTest`: saveCheckpoint, loadLatest, fingerprint comparison.
  - Flyway migration runs automatically at container startup via `spring.flyway.enabled=true`.

### Completion Checklist

- [x] `mvn flyway:migrate` creates the full schema against a real PostgreSQL instance.
- [x] All repository adapters pass integration tests with real PostgreSQL.
- [x] `@ConfigurationProperties` validation fails fast on missing required environment variables.
- [x] No raw SQL in Java code — all queries via JPA or `@Query`-annotated interfaces.

---

## [x] Phase 2b — Persistence Cleanup & DTO Foundation

**Goal:** Restructure the persistence layer into clean sub-packages, introduce MapStruct
compile-time mappers for all entity↔domain translations, scaffold the API DTO layer, and
wire OpenAPI spec generation.

### Tasks

- [x] 2b.1 — Add MapStruct 1.6.3 and springdoc-openapi 2.8.9 to pom.xml
- [x] 2b.2 — Split persistence into adapter/ jpa/ mapper/ sub-packages
- [x] 2b.3 — Create MapStruct entity mappers (Document, Artifact, User, KnowledgeBase)
- [x] 2b.4 — Delete old flat-package persistence files
- [x] 2b.5 — Update PersistenceConfig for new package paths + mapper injection
- [x] 2b.6 — Scaffold api/dto/response/ records (Document, Artifact, User, KnowledgeBase)
- [x] 2b.7 — Scaffold api/dto/request/ records (DocumentUpload, Login, Register)
- [x] 2b.8 — Create MapStruct DtoMappers in api/mapper/ (domain → response DTO)
- [x] 2b.9 — Create OpenApiConfig bean
- [x] 2b.10 — Update docs/standards/architecture/hexagonal.md
- [x] 2b.11 — Update docs/standards/backend/models.md
- [x] 2b.12 — Update docs/standards/backend/api.md
- [x] 2b.13 — Create docs/standards/backend/openapi.md
- [x] 2b.14 — Run mvn compile to verify MapStruct generates correctly

### Completion Checklist

- [x] mvn compile succeeds with zero errors
- [x] No manual toEntity/toDomain methods remain in any adapter class
- [x] All persistence sub-packages contain only their designated type (no mixing)
- [x] api/dto/response/ types contain no forbidden fields (passwordHash, referenceAnswer, cost)
- [x] GET /v3/api-docs returns a valid OpenAPI 3.1 JSON document (verified in Phase 9, springdoc 3.0.0)

---

## [x] Phase 3 — AI Gateway

**Goal:** Implement the Spring AI–backed `AIGateway` adapter with model-tier routing,
retry, deadline enforcement, cost tracking, and the `StubAIGateway` for tests.

### Tasks

- [x] **3.1 — `AIGatewayAdapter`** (`dev.mindforge.infrastructure.ai`)
  - Implements `AIGateway` (domain port).
  - Injected via `@Configuration` — not constructed directly anywhere.
  - Routes `ModelTier` → specific model strings via `AppProperties` (e.g., `LARGE` →
    `"openai/gpt-4o"`, `SMALL` → `"openai/gpt-4o-mini"`).
  - Uses `ChatModel` from Spring AI to call OpenRouter endpoint.
  - Enforces `DeadlineProfile` via a per-call virtual-thread timeout race, with the
    timeout durations themselves configuration-driven via `AppProperties`
    (`INTERACTIVE` default 10 s, `BATCH` default 60 s, `BACKGROUND` default 300 s) —
    the underlying OpenAI client only accepts a timeout at client-construction time,
    not per request, so a `RestTemplate`-level timeout could not vary per call.
  - Tracks token counts and cost via `CompletionResult` (cost accounting deferred to
    Phase 14 — OpenRouter's chat-completions response carries no per-call cost field).

- [x] **3.2 — Embedding support** (`dev.mindforge.infrastructure.ai`)
  - Folded into `AIGatewayAdapter` rather than a separate `EmbeddingAdapter` class —
    `AIGateway.embed()` is a single method delegating directly to Spring AI's
    `EmbeddingModel.embed(String)`, and a second adapter class would only wrap that
    one line.
  - Returns `float[]` embedding vectors — no domain-layer type pollution.

- [x] **3.3 — `DeadlineExceededException`**
  - Domain exception (extends `RuntimeException`); thrown when a gateway call exceeds
    its `DeadlineProfile` timeout.

- [x] **3.3b — Resilience4j Retry + CircuitBreaker** (`AiConfig` + `AIGatewayAdapter`) — see ADR-0009.
  - Programmatic decoration (`Retry.decorateSupplier` / `CircuitBreaker.decorateSupplier`),
    no `resilience4j-spring-boot3` starter, so it stays unit-testable without a Spring context.
  - Retry fires only on `TransientAiException` + `IOException`; `NonTransientAiException`
    (bad request/auth/unknown model) and an open-circuit rejection are never retried.
  - Retry sits **inside** the `callWithDeadline` race — all attempts for one logical call
    share a single `DeadlineProfile` budget rather than each getting a fresh deadline.
  - One shared `CircuitBreaker` across all tiers (single upstream provider). A deadline
    timeout is recorded as a breaker failure; `DeadlineExceededException` is in its
    `recordExceptions` set.
  - When the circuit is open, `CallNotPermittedException` is caught and rethrown as the
    domain `AIGatewayUnavailableException`, so callers never see a resilience4j type.
  - Spring AI's own built-in retry is disabled (`spring.ai.retry.max-attempts=1`) so
    Resilience4j is the sole retry authority — attempts are not multiplied.
  - All thresholds tunable via `mindforge.ai.resilience.*` (`AppProperties.Ai.Resilience`).

- [x] **3.4 — `StubAIGateway`** (`src/test/java/.../support/`)
  - Implements `AIGateway`.
  - Builder API: `StubAIGateway.builder().willReturn(ModelTier.LARGE, "my response").build()`.
  - Captures all calls for assertion in tests.
  - Never makes real HTTP calls.

- [x] **3.5 — Unit tests**
  - `AIGatewayAdapterTest`: model-tier routing resolves correct model strings; response
    mapping into `CompletionResult`; deadline timeout throws `DeadlineExceededException`
    (exercised fast via injected millisecond-scale deadlines, not real 10s waits); embed
    delegation; retry on transient failure then success; no retry on non-transient failure;
    open circuit throws `AIGatewayUnavailableException`; deadline timeout recorded as a
    circuit-breaker failure.
  - `StubAIGatewayTest`: canned response delivery, default fallback, call capture, no
    real HTTP calls.

### Completion Checklist

- [x] `AIGateway` is never instantiated directly — always resolved via Spring DI
  (`AiConfig` `@Bean`).
- [x] `ModelTier` routing is configuration-driven, not hardcoded strings.
- [x] Deadline profiles enforce correct timeouts (verified by timeout test with mock HTTP).
- [x] Retry + circuit breaker guard every provider call; Spring AI's built-in retry is
  disabled so Resilience4j is the single retry authority (ADR-0009).
- [x] `StubAIGateway` is available for all downstream phases.

---

## [ ] Phase 3b — Wiki Pivot Cleanup

> **New in v3.0.** Executes the keep / change / delete table in
> [T12](../wayfinder/tickets/12-existing-code-fate.md). Builds no new behaviour.

**Goal:** Remove everything Phases 1–3 built for the per-document artifact model, fix the defects the
re-cut and its review exposed, and squash the migration chain into a baseline — ending green.

> **Changed in v3.1:** `KnowledgeBase.documentCount` goes (T24), `DomainEvent` drops `documentId()` (T20), and
> `LessonIdentity` adopts the shared identifier grammar and text rules (T18, T19).

### Tasks

- [x] **3b.1 — Delete domain types** (`dev.mindforge.domain.model`)
  - `Agent`, `AgentCapability`, `AgentContext`, `AgentResult` (ADR 0013).
  - `DocumentArtifact`, `SummaryData`, `ConceptMapData` (ADR 0010).
  - `StepCheckpoint`, `StepFingerprint`, `DocumentStatus` (ADR 0015).
  - `FlashcardData` — Phase 10 writes `Flashcard` keyed on `pageId` (ADR 0018).

- [x] **3b.2 — Change domain types**
  - `Document`: drop `status`.
  - `KnowledgeBase`: drop `documentCount` — counts are derived at read (T24).
  - `DomainEvent`: drop `documentId()` from the interface (each record keeps its own ids); keep `DocumentIngested`;
    delete `PipelineStepCompleted`, `ProcessingCompleted`, `ProcessingFailed`, `GraphProjectionUpdated` (T20).
  - New pure `Identifier` (T18): `PATTERN` `[a-z0-9]+(-[a-z0-9]+)*`, 1–80 characters; `index` and `log` reserved
    everywhere, `default` and `conversation` for lesson ids. `slugify`: NFC; NFD base letters; `ł→l`, `ø→o`, `ß→ss`,
    `đ→d`, `æ→ae`, `œ→oe`, `þ→th`; Greek letters by Polish name; any other letter or digit dropped **and** an 8-hex
    SHA-256 suffix appended (`x-<hex>` when nothing is left); capped at 80 keeping the suffix.
  - New pure `TextRules` (T19): `singleLine` (NFC, whitespace runs → one space, `Cc`/`Cf` removed, trimmed).
  - `LessonIdentity`: validates with `Identifier` — an explicit `lesson_id` must match and is never rewritten; a derived
    id uses `Identifier.slugify` and gains `-lesson` on a reserved word; the title passes `TextRules.singleLine` and is
    cut to 200 characters with `…`.

- [x] **3b.3 — Ports** (`dev.mindforge.domain.port`)
  - Delete `ArtifactRepository`, `GraphIndexer`.
  - `AIGateway`: delete `embed` (ADR 0016).
  - `DocumentRepository`: `findByContentHash(UUID knowledgeBaseId, ContentHash hash)` — today's
    unscoped lookup deduplicates across tenants; `findById(UUID knowledgeBaseId, UUID documentId)`; delete
    `updateStatus` (T25: `kbId` first on every tenant-scoped port).

- [x] **3b.4 — Infrastructure**
  - Delete `ArtifactEntity`, `StepCheckpointEntity`, `ContentEmbeddingEntity`, `ArtifactJpaRepository`,
    `StepCheckpointJpaRepository`, `ArtifactEntityMapper`, `ArtifactRepositoryAdapter`.
  - `AIGatewayAdapter` and `AiConfig`: drop `EmbeddingModel`.
  - `PersistenceConfig`: drop the `ArtifactRepository` bean.
  - `DocumentEntity`, `DocumentJpaRepository`, `DocumentEntityMapper`, `DocumentRepositoryAdapter`:
    no status; hash lookup and `findById` scoped by knowledge base.
  - `KnowledgeBaseEntity`: drop `documentCount`.

- [x] **3b.5 — API DTOs**
  - Delete `ArtifactResponse`, `ArtifactDtoMapper`.
  - `DocumentResponse`, `DocumentDtoMapper`: drop `status` (Phase 9 adds a run-status view).
  - `KnowledgeBaseResponse`: drop `documentCount` (Phase 9 derives counts).

- [x] **3b.6 — Migration baseline** (`src/main/resources/db/migration/`)
  - Delete `V1`–`V7`; add `V1__baseline.sql` with `users`, `knowledge_bases` and `documents` only.
  - `knowledge_bases`: no `document_count`.
  - `documents`: no `status`; `UNIQUE (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION'`;
    plain index on `(knowledge_base_id, lesson_id)`; no global `content_hash` index; `uploaded_by` FK
    `DEFERRABLE INITIALLY DEFERRED` (a user delete cascades through `knowledge_bases`, T24).
  - Record the one-time squash in `docs/standards/backend/migrations.md` (done in v3.0).

- [x] **3b.7 — Configuration and build**
  - `application.yml`: delete `spring.neo4j.*` and `spring.ai.vectorstore.*`. `application-dev.yml`: delete `spring.neo4j.*`.
  - `pom.xml`: delete `spring-boot-starter-data-neo4j`, `spring-ai-starter-vector-store-pgvector`, `testcontainers-neo4j`.
  - `env.example`: delete the `NEO4J_*` block.

- [x] **3b.8 — Tests**
  - Delete `AgentResultTest`, `StepFingerprintTest`, `FlashcardDataTest`, `ArtifactRepositoryAdapterTest`,
    `integration/graph/`.
  - `DocumentRepositoryAdapterTest`: drop status; **add** "the same hash in two knowledge bases is not
    a duplicate across them" and "`findById` with another knowledge base's id returns empty".
  - `LessonIdentityTest`: Polish transliteration (`Mitoza komórkowa` → `mitoza-komorkowa`); `log` and `conversation`
    reserved; explicit `bio_3` rejected; a file named `index.md` resolves to `index-lesson`; a newline in a PDF title
    is flattened.
  - New `IdentifierTest`: `α-helisa` → `alfa-helisa`; `π` → `pi`; `細胞` → `x-` + hash; `細胞 A` ≠ `細胞 B`; 80-character
    cap keeps the suffix; `PATTERN` rejects `a--b`, `-x`, uppercase.
  - `StubAIGateway`, `StubAIGatewayTest`, `AIGatewayAdapterTest`: drop `embed` and the embedding tests.
  - `TestContainerBase`: drop the Neo4j container; `pgvector/pgvector:pg15` → `postgres:15`.
  - `TestFixtures`: drop `makeDocumentArtifact` and the `status` parameter.

### Completion Checklist

- [x] `mvn verify` passes.
- [x] No reference remains to `DocumentArtifact`, `Agent`, `StepFingerprint`, `GraphIndexer`, Neo4j,
  pgvector or `embed` in `src/`.
- [x] Cross-knowledge-base dedup test passes.
- [x] No identifier is validated or slugified outside `Identifier`.
- [ ] Local databases dropped and recreated (the squash invalidates Flyway checksums).

---

## [x] Phase 4 — Document Parsing and Ingestion

> **Changed in v3.0:** 4.5 and 4.6 — dedup per knowledge base, one `Document` per
> uploaded lesson version, no status, no checkpoints (ADR 0015).
> **Changed in v3.1:** chunk defaults sized for Extract (T23); lesson collisions need an explicit new version and
> dedup is checked under the knowledge-base row lock (T18, T24).
> **As built:** `IngestionService` reaches the sanitizer and the registry through two domain ports, `UploadPolicy` and
> `DocumentParser`, because application code may not import infrastructure. For the same reason the chunker lives in
> `application.ingest`, next to its consumer. `chunkOverlapTokens` is removed rather than defaulted to 0, and
> `SpringEventPublisher` (6.8) was brought forward to wire the service. The 409 names the existing lesson's title, so
> T25's `lessonExists` is `findLessonTitle`. The transaction is opened programmatically (`TransactionOperations`), so
> admitting and parsing happen before it and the row lock is its first statement.

**Goal:** Implement the `ParserRegistry`, all four document format parsers, `UploadSanitizer`,
heading-aware chunking, and `IngestionService` with per-knowledge-base deduplication.

### Tasks

- [x] **4.1 — `UploadSanitizer`** (`dev.mindforge.infrastructure.security`) — implements the `UploadPolicy` port
  - Validates MIME type against the allowlist — the types a parser is registered for (`text/markdown`,
    `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`).
  - Rejects oversized uploads (`mindforge.upload.max-size`, default 50 MB).
  - Rejects path traversal attempts in filename.
  - Sanitizes filename to safe characters, keeping letters of any script (the stem can become the lesson title).
  - Every refusal is an `UploadRejectedException`.

- [x] **4.2 — `ParserRegistry`** (`dev.mindforge.infrastructure.parsing`) — implements the `DocumentParser` port
  - MIME-dispatch map from content type → `FormatParser` implementation.
  - All parsers registered via `@Configuration` (`IngestionConfig`), not discovered via classpath scan.

- [x] **4.3 — Format parsers** (`dev.mindforge.infrastructure.parsing`)
  - `MarkdownParser`: extracts heading tree, code blocks, front-matter metadata.
  - `PdfParser`: wraps Apache PDFBox; extracts text per-page, document metadata.
  - `DocxParser`: wraps Apache POI; extracts paragraphs and heading styles, **in document order**
    (tables stay where the prose places them).
  - `PlainTextParser`: line-based extraction, best-effort heading detection.
  - Headings are `BlockType.HEADING` blocks with a `level`; text formats are strict UTF-8.

- [x] **4.4 — Heading-aware chunker** (`dev.mindforge.application.ingest.HeadingChunker`) — T23
  - Splits the `ContentBlock` list into chunks at heading boundaries, each at most `ProcessingSettings.chunkSizeTokens`.
  - Defaults change to **12 000 tokens, no overlap** — sized for Extract, the chunker's only consumer (overlap would
    duplicate claims).
  - Token counts via a new pure `TokenEstimate.of(String) = ceil(chars / 3)` in the domain — the one estimate
    `TokenBudget`, Supersede and the index ceiling also use.
  - Produces deterministic chunks — same input always produces same chunks. A block is never cut, so one larger than a
    chunk is a chunk of its own.

- [x] **4.5 — `IngestionService`** (`dev.mindforge.application.service`) — T18, T24
  - Validates upload via `UploadSanitizer`; parses via `ParserRegistry`; resolves `LessonIdentity`, honouring an optional
    `lessonId` override (validated by `Identifier`) and a `newVersion` flag.
  - One `@Transactional` boundary, opened with `SELECT … FROM knowledge_bases WHERE kb_id = :kb FOR UPDATE`, which
    serializes uploads into one knowledge base:
    1. An identical upload (same `ContentHash`, not a conversation turn) returns the existing document id. Under the
       lock this check cannot race; `UNIQUE (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION'`
       is the backstop, and a violation is a bug (500).
    2. Lesson rule: the lesson id already has a document and `newVersion` is false → `LessonAlreadyExistsException`
       (409, carrying the lesson's id and title); `newVersion` with no such lesson → 422.
    3. Persist the `Document` — `DocumentEntity` implements `Persistable<UUID>` so `save()` inserts — and publish
       `DocumentIngested`. Phase 6.8 adds the `QUEUED` run to this transaction and replaces the event.
  - A revised upload of the same lesson (different hash, `newVersion`) is a **new** `Document`; there is no
    retraction of the old version's contributions.
  - Returns HTTP 202 semantics: the ingest run starts after commit (Phase 6).

- [x] **4.6 — Unit tests**
  - `IngestionServiceTest`: identical upload returns existing id; same bytes into another knowledge
    base create a new document; an upload whose lesson id exists is rejected unless `newVersion`, which creates a
    second `Document`; `newVersion` for an unknown lesson is rejected; save + event publish together.
  - Integration (`ConcurrentUploadTest`): two concurrent uploads of the same new lesson into one knowledge base — one
    succeeds, the other gets 409 (or its identical-content id).
  - Parser unit tests: each parser extracts expected text and metadata from fixtures (PDF and DOCX fixtures are built in
    the test with PDFBox and POI, not stored as binaries).
  - `UploadSanitizerTest`: MIME rejection, oversized rejection, path traversal rejection.
  - `HeadingChunkerTest`, `TokenEstimateTest`; `DocumentRepositoryAdapterTest` gains `findLessonTitle` scoping and
    "`insert` never overwrites".

### Completion Checklist

- [x] All four parsers extract text and metadata correctly from fixture files.
- [x] Chunker produces deterministic, heading-aware chunks sized by `TokenEstimate`.
- [x] Dedup is per knowledge base, checked under the knowledge-base row lock, with the constraint as backstop.
- [x] No upload silently becomes a version of an existing lesson.
- [x] `UploadSanitizer` rejects disallowed MIME types, oversized files, and path traversal.
- [x] `DocumentIngested` event is published in the same transaction as the document save.

---

## [x] Phase 5 — Wiki Domain and Store

> **Re-cut in v3.0.** This phase was *Agent Framework and Pipeline Orchestration* (`AgentRegistry`,
> `OrchestrationGraph`, step-fingerprint checkpointing). All of that is deleted (ADRs 0013, 0015).
> The phase now builds the wiki model, its storage, run records and revert.
> **As built:** `PageRevision` and `page_revisions` also carry the page's `path` — it never changes, but a deleted page
> has no row to take it from, and revert of a deletion must check that the path is free. `RunReportQuery` has only
> `logEntries`; `listRuns` and `report` land with their only consumer, 9.7, which decides their shapes. `addSources` and
> `addSupersessions` take no separate run id, since each row carries its own. `failures` and `findings` are JSON objects
> (`List<Map<String, Object>>`) whose shapes the recording steps own. `knowledge_bases.active_run_id` is deferred too, as a
> user delete removes both its sides. `page_links` has a surrogate identity key and links are de-duplicated on write.
> `KnowledgeBase.pageCount` is a Hibernate `@Formula` subquery. The renderers are static pure functions, sharing
> `TextRules.escapeLinkText` with export; `log.md` dates a run by `finished_at`. A revert steps through `QUEUED → RUNNING →
> WRITTEN → COMPLETED` inside its one transaction, reusing the fenced lifecycle, so nothing outside ever sees the
> intermediate states. `DocumentRepository` needed nothing new: `insert` and Phase 4's `findLessonTitle` cover 5.3.
> `WikiStore` gains `findSupersession`, and `deleteSupersession` returns whether it removed the row: supersession removal
> reads the row to name its run, claims the lease, and only then deletes, so it cannot deadlock against a revert.
> `page_revisions` has `UNIQUE (page_id, ingest_run_id)`, so "pre-run state is `r − 1`" is a constraint, not an assumption.

**Goal:** Implement pages, paths, links, provenance, revisions, supersessions and ingest runs in the
domain; their PostgreSQL schema and `WikiStore` adapter; the ingest lease; the index and log renderers;
and restore-forward, tip-only revert.

### Tasks

- [x] **5.1 — Wiki domain types** (`dev.mindforge.domain.model`) — ADRs 0010, 0011
  - `WikiPage(pageId, knowledgeBaseId, path, title, description, type, markdownBody, revision, createdAt, updatedAt)`.
  - `PageType` value object (non-empty, normalised) with constants `CONCEPT`, `SOURCE_SUMMARY`.
  - `PagePath`: `concepts/<name>` or `sources/<lesson-id>`, built only from `Identifier` (3b.2); a derived Concept name
    on a reserved word gains `-concept`; directory fixed by type.
  - `PageLink(pageId, targetPath, fragment)`, `PageSource(pageId, documentId, ingestRunId)`,
    `PageRevision(pageId, revision, ingestRunId, title, description, type, markdownBody /* null = tombstone */, createdAt)`,
    `PageSupersession(supersessionId, supersededPageId, sectionAnchor, supersedingPageId, ingestRunId, createdAt)`.
  - `IngestRun(runId, knowledgeBaseId, kind INGEST|REVERT|LINT, documentId, revertsRunId, status
    QUEUED|RUNNING|WRITTEN|COMPLETED|FAILED, attempt, failureReason, retryable, failures, supersessionSkipped,
    supersessionCount, stepVersions, findings, createdAt, startedAt, finishedAt)` (T17, T16).
  - `KnowledgeBase`: add a derived `pageCount`.
  - `MarkdownStructure` (pure, T26) — the only parser of page bodies:
    - fences (up to three spaces, then three or more `` ` `` or `~`; closed by at least as many of the same);
    - sections = level-1 ATX headings at column 0 outside fences; anchor `Identifier.slugify(stripLinks(text))`, first
      duplicate wins; deeper headings are prose;
    - inline links outside fences and code spans classified as internal (`/(concepts|sources)/<id>.md(#<anchor>)?` →
      `PageLink`), external (`http(s)://`, autolinks) or invalid (anything else, images, reference definitions);
    - `stripLinks`, the spans eligible for link insertion, and section ranges for marking or stripping.
  - `TextRules` (3b.2) gains `normaliseBody`: `\r\n` → `\n`, trailing whitespace stripped per line, one final newline.

- [x] **5.2 — Migration `V2__create_wiki_and_runs.sql`** — ADRs 0014, 0015
  - `wiki_pages` (`UNIQUE (knowledge_base_id, path)`, `UNIQUE (knowledge_base_id, page_id)`).
  - `page_links` with FK `(knowledge_base_id, source_page_id)` → `wiki_pages` `ON DELETE CASCADE`;
    index `(knowledge_base_id, target_path)`.
  - `page_revisions`, `page_sources`, `page_supersessions` — FK to `knowledge_bases` only, **no FK to `wiki_pages`**.
  - `ingest_runs` per T07 plus T10's `findings` and the review's columns: `status` includes `QUEUED`;
    `attempt SMALLINT NOT NULL DEFAULT 1`; `retryable BOOLEAN NULL`; `supersession_count INT NOT NULL DEFAULT 0`;
    `created_at NOT NULL`; `started_at` nullable; `step_versions JSONB NOT NULL DEFAULT '{}'`; index
    `(knowledge_base_id, status, created_at)`.
  - `knowledge_bases.active_run_id` FK → `ingest_runs`.
  - **FK rule (T24):** every FK whose two sides are removed by the same cascade is `NO ACTION DEFERRABLE INITIALLY
    DEFERRED` — `page_sources.document_id`, `ingest_runs.document_id`, `ingest_runs.reverts_run_id`, and every
    `ingest_run_id`. (Replaces `page_sources.document_id ON DELETE RESTRICT`.)

- [x] **5.3 — Ports** (`dev.mindforge.domain.port`) — `kbId` first on every tenant-scoped method; signatures in
  [T25](../wayfinder/tickets/25-port-read-surface.md)
  - `WikiStore` (page-shaped): `findByPath`, `findById`, `findByPaths`, `listIndex`, `listBodies(type)`,
    `pageIdsForLesson`, `savePage` (page + revision + re-derived links), `deletePage` (tombstone + row delete),
    `reinsertPage`, `addSources`, `addSupersessions`, `deleteSources(runId, pageIds)`,
    `deleteSupersessionsBySuperseding(runId, pageIds)`, `deleteSupersession(id)`, `tipRevisions(pageIds)`,
    `revisionsByRun`, `findRevision`, `listRevisions`, `outboundLinks`, `inboundLinks`, `graph`, `liveSupersessionsOf`.
  - `RunReportQuery` (read-only projections): `listRuns`, `report(runId)`, `logEntries`.
  - `IngestRunRepository`: `enqueue`; `claim` (lease + `QUEUED` → `RUNNING`, one transaction; returns whether claimed);
    `oldestQueued`; `markWritten`, `complete`, `fail` (fenced on status and lease; `complete` and `fail` release the
    lease); `findById`; `latestForDocument`; `hasQueuedOrActive`. System methods, for the sweep only:
    `findUnfinished`, `knowledgeBasesWithQueuedRuns`.
  - `DocumentRepository`: add `lessonExists`, `insert` (3b scoped `findById` and `findByContentHash`).

- [x] **5.4 — Persistence adapters** (`dev.mindforge.infrastructure.persistence`)
  - Entities, JPA repositories, MapStruct mappers, port and query-port adapters for all of 5.2, per the sub-package
    convention; entities with assigned ids implement `Persistable<UUID>`.
  - Every query binds `knowledge_base_id`.

- [x] **5.5 — Renderers** (`dev.mindforge.application.wiki`)
  - `IndexRenderer`: root index — `okf_version` frontmatter; always both `# Concepts` and `# Sources`, even empty;
    `* [title](/path.md) - description`, sorted by a Polish `Collator` then path; `\`, `[` and `]` in titles escaped.
    Logs WARN with the `TokenEstimate` when the index passes 20K tokens. Used by model prompts, the SPA and export.
  - `LogRenderer`: `# Update Log`, then `## YYYY-MM-DD` groups (UTC, newest first) with one line per `COMPLETED` run that
    changed something, in T16's final vocabulary — **Ingest** / **Edit** (created, revised, deleted, claims superseded),
    **Lint** (links added to N pages), **Revert** (undid the ingest / edit / Lint of … — pages restored, pages removed,
    supersessions removed; or removed N supersessions from …). Page counts derived from `page_revisions`; supersession
    counts from `ingest_runs.supersession_count`; titles escaped.

- [x] **5.6 — `RevertService`** (`dev.mindforge.application.service`) — ADR 0012, T16
  - **One transaction, no model call:** insert the `REVERT` run; claim the lease (0 rows → rollback,
    `KnowledgeBaseBusyException`); do the work; set `COMPLETED` and `supersession_count`; release the lease.
  - **Revert of run R** — a `COMPLETED` `INGEST` or `LINT` run, never a `REVERT`: P = pages whose highest revision carries
    R (a tombstone additionally needs its path free). Empty P → `RevertNotAllowedException`.
  - Restore-forward for P: append revision `r + 1` carrying `r − 1`'s content; a created page gets a tombstone;
    a deleted page is re-inserted under its `page_id`.
  - Delete R's `page_sources` for P and R's `page_supersessions` whose superseding page is in P — nothing for pages R no
    longer tips.
  - **Supersession removal:** the same transaction shape, `reverts_run_id` = the row's run, deleting that one row.

- [x] **5.7 — Tests**
  - Unit: `PagePath` (reserved suffix); `PageType` normalisation; `MarkdownStructure` (a `# comment` inside a fence is
    not a heading; `##` is not a section; each link class; eligible spans exclude fences, autolinks and tags);
    `IndexRenderer` (escaping, empty sections, Polish order) and `LogRenderer` (every line shape); tip check.
  - Integration (`@Testcontainers`): path uniqueness; a link row cannot reference another knowledge base's page;
    delete → tombstone → revert re-inserts with sources intact; revert keeps the sources of pages it does not restore;
    a `REVERT` run is not revertible; revert while a run holds the lease → 409; two concurrent `claim` calls — exactly
    one wins and the loser stays `QUEUED`; deleting a knowledge base holding every kind of row, and deleting its owner,
    leave nothing behind.

### Completion Checklist

- [x] No `deleted_at` filter exists anywhere — a deleted page is absent because its row is.
- [x] Every tenant-scoped port method takes `kbId` first.
- [x] No page body is parsed outside `MarkdownStructure`.
- [x] Revert is restore-forward: no revision row is ever deleted and `revision` is monotonic.
- [x] The lease admits one active run per knowledge base under concurrency.
- [x] Every cross-cascade FK is deferred, and root deletes succeed.

---

## [x] Phase 6 — Ingest Pipeline

> **Re-cut in v3.0.** This phase was *Core Processing Agents* — seven agents behind an `Agent`
> interface. Now: a fixed pipeline of concrete model services (ADR 0013). `SummarizerAgent` and
> `ConceptMapperAgent` are gone; flashcard and quiz generation move to Phase 10.
> **Changed in v3.1:** conversation edits enter at Extract (T15); chunked Extract, two caps and writer inputs (T23);
> Resolve and title rules (T22); draft validation and heading preservation (T19, T21, T26); Supersede inputs and checks
> (T14); the run queue, fencing, sweep and permit pool (T17); progress and run events, absorbed from Phase 8 (T20).
> **As built:** `IngestPipeline` (application) sequences the concrete model services in `dev.mindforge.agent`, so the
> application layer may import that package; the services reach `infrastructure.ai` for `PromptLoader`, `ModelJson` (JSON
> answers, an unreadable one is a `ModelOutputException`) and `PermitGateway`, which wraps the gateway of every background
> service around one shared `Semaphore` bean. Resolve, the draft checks and Supersede's inputs and proposal checks are
> pure classes in `application.ingest` (`Resolver`, `DraftValidator`, `SupersessionInputs`); `LinkInsertionApplier` is in
> `application.wiki` for Lint to share. `markWritten`, `complete` and `fail` also take `stepVersions`, recorded per service
> as `VERSION@model` with the model its tier is configured to route to. Prompt decisions (6.1): prose is written in Polish,
> the prompt locale, whatever the source's language, keeping original terms in parentheses; a Concept is level-1 sections
> of one aspect each, opening with its definition; a Source Summary is recommended `# Streszczenie`, `# Najważniejsze tezy`
> and `# Omawiane pojęcia` — conventions the prompt states as such, not rules. The guard reads the first chunk; a document
> with no text fails before it (`retryable = false`); an edit whose every item was dropped fails as "no applicable change".
> Supersede is skipped when no claim landed on a revised Concept. The link check reads only drafted, changed bodies, ten
> per call. A conversation turn's content is `ConversationTurn` (instruction, then the quoted answer after a `---` line).
> The `AFTER_COMMIT` drain runs on its own virtual thread, outside the committed transaction; the sweep interval is
> `mindforge.runs.sweep-interval`. Until Phase 7 the worker fails a `LINT` run; "a Lint queued behind an ingest runs
> next" is tested there. `StubAIGateway` routes answers by prompt fragment, and integration tests share it as a
> `@Primary` bean imported by `TestContainerBase`.

**Goal:** Ingest one document (or conversation turn) into its knowledge base's wiki end to end —
relevance guard, chunked claim extraction against the index, resolve, parallel page writes, link check,
commit, supersession — with the run queue, fenced commits, partial-success semantics, the sweep and progress.

### Ingest contract

- **Input**: an `INGEST` run for a `Document` — parsed `ContentBlock`s, or a `CONVERSATION` turn whose instruction
  goes through Extract's edit prompt (T15).
- **Output**: the run `COMPLETED` or `FAILED`, plus the pages, revisions, sources, links and supersessions it committed.
- **A run fails when no page task succeeded** — a task succeeds when its draft passed validation (changed or not), or its
  delete or retitle applied. Partial success lands and records `failures`. A run whose drafts were all unchanged
  completes with no revisions (T21).
- **Caps fail loudly, never truncate**: claims per Extract call (`retryable`) and page tasks per run (not `retryable`)
  (T23).

### Tasks

- [x] **6.1 — Prompt files** (`src/main/resources/prompts/pl/`)
  - `relevance_guard.pl.md`, `claim_extractor.pl.md`, `claim_extractor_edit.pl.md`, `page_writer.pl.md`,
    `link_checker.pl.md`, `supersession_detector.pl.md`.
  - Decide and write down here: section conventions per page type, and the prose language when a source's
    language differs from the prompt locale (handed off from the re-cut map's fog).
  - Every rule a prompt teaches is also enforced in code (see `ai_agents.md`).

- [x] **6.2 — `Preprocessor` and `RelevanceGuard`**
  - `Preprocessor` (`dev.mindforge.application.ingest`): plain code, no LLM, no `VERSION` — whitespace, headings,
    cleaned blocks (T28).
  - `RelevanceGuard` (`dev.mindforge.agent`, SMALL): returns `ValidationResult`; a rejection fails the run with its
    reason and `retryable = false`. Skipped for conversation turns (T15).

- [x] **6.3 — `ClaimExtractor`** (LARGE) — T23, T22, T15
  - `extract(chunk, renderedIndex, plannedPages)` → `ExtractResult(List<Claim(text, title, targetPath?, firstBlock,
    lastBlock)>, chunkDigest)`. One call per heading-aware chunk (4.4), **sequentially**; each call also sees the paths
    and titles planned by earlier chunks. `DeadlineProfile.BACKGROUND` applies per call.
  - `extractEdit(instruction, quotedAnswer, renderedIndex)` → `List<EditItem>` — `Claim`, `Delete(path)`,
    `Retitle(path, title)` — from its own prompt. The document method cannot return a deletion.
  - Over `ProcessingSettings.maxClaimsPerExtractCall` (default 40) → run `FAILED`, `retryable = true`.
  - Titles pass `TextRules.singleLine` and must be 1–200 characters; block ranges outside the chunk are ignored.

- [x] **6.4 — Resolve** (code, in `IngestPipeline`) — T22, T15, T28
  - A claim's `targetPath` counts only if it is a live `Concept` or a path planned by an earlier chunk; otherwise its
    path is `concepts/` + `Identifier.slugify(title)` — a revision if that path is live, else a create.
  - Group claims by final path into **exactly one `PageWriteTask` per path**; a create takes the title of the group's
    first claim in document order; a create with an invalid title fails its task.
  - Over `ProcessingSettings.maxPageTasksPerRun` (default 100 Concept tasks) → run `FAILED`, `retryable = false`, before
    any write call.
  - Edit items: `Delete` and `Retitle` only of a live Concept; a path both deleted and written drops both; dropped items
    are recorded in `failures`.
  - `ARTICLE` documents are create-only: claims resolving to a live page are dropped and recorded (T28).
  - Add the document's `Source Summary` task (`sources/<lesson-id>`, title = the document's `lessonTitle`), unless the
    document is a conversation turn.

- [x] **6.5 — `PageWriter`** (LARGE) — T21, T23, T26, T19, T22
  - Input:
    - the task: its claims and, for a Concept, their source blocks (de-duplicated, document order, trimmed to
      `ProcessingSettings.writerSourceTokens`, default 16 000, by dropping whole blocks from the end); for a Source
      Summary, the whole document if it fits `chunkSizeTokens`, else the chunk digests in order;
    - the existing body verbatim, and its superseded sections as a separate list
      `SupersededSection(heading, anchor, supersedingPath, supersedingTitle)`;
    - the linkable index: live pages minus this run's deletions, plus this run's planned creates.
  - Output: `PageDraft(description, body)` — no title; code owns titles.
  - Code validation, over `MarkdownStructure` — a failure fails that page task and is recorded in `failures`:
    - description passes `TextRules.singleLine`, 1–300 characters; body passes `TextRules.normaliseBody` and is not blank;
    - no frontmatter; no level-1 `Citations` heading; no level-1 heading whose anchor equals the title's;
    - no invalid link, image or reference definition;
    - an `INGEST` run whose document is not a conversation turn, revising a Concept, keeps every existing level-1
      anchor — superseded sections included.
  - A draft whose title, description and normalised body equal the live page writes nothing (no revision, source row or
    link re-derivation). The run report shows each written body's length before and after.

- [x] **6.6 — `LinkChecker`** (SMALL) **and `LinkInsertionApplier`** (code) — ADR 0017, T26
  - The model returns `List<LinkInsertion>`; code wraps the first eligible occurrence — outside links and autolinks, code
    spans, fenced blocks, heading lines and `<…>` spans — only if the target is live and not deleted by this run, or was
    successfully drafted in this run; the target is not the page itself; any fragment is a level-1 anchor of the target's
    body. Asserts strip-links equality.
  - Runs on in-memory bodies before commit; a failure records `{"step":"linkCheck"}` and commits without extra links.

- [x] **6.7 — `SupersessionDetector`** (LARGE ×1) — T14
  - Runs after commit 1, under the lease. Skipped for `ARTICLE` documents and for runs with no revisions.
  - Input: this run's claims with their resolved paths (only pages that got a revision), and the level-1 sections of
    candidate pages — live Concepts written by this run or one `page_links` hop from one, in either direction — shown with
    path, anchor and heading. Sections already superseded are left out. Pages are ranked by connecting-link count, then
    path, and added whole up to `ProcessingSettings.supersessionContextTokens` (default 30 000); omitted pages are
    recorded as `{"step":"supersede","omittedPages":N}`.
  - Output: `SupersessionProposal(supersededPath, sectionAnchor, supersedingPath)`. Kept only if the section was among the
    candidates shown, the superseding path is a Concept this run revised, the two differ, and no live supersession or
    earlier proposal covers that section. Drops are recorded as `{"step":"supersede","dropped":N}`.

- [x] **6.8 — Run worker, `IngestPipeline` and progress** (`dev.mindforge.application.service`) — ADR 0015, T17, T20
  - **Events**: `SpringEventPublisher implements EventPublisher`. `DomainEvent.DocumentIngested` is replaced by
    `IngestRunQueued(runId, knowledgeBaseId, occurredAt)`, published by every transaction that inserts a `QUEUED` run;
    an `@TransactionalEventListener(AFTER_COMMIT)` calls `RunWorker.drain(kbId)`.
  - **Queue**: the upload transaction (4.5) also inserts a `QUEUED` `INGEST` run. `drain(kbId)` takes the oldest
    `QUEUED` run, adds its id to the in-memory active set, then claims it (lease + `QUEUED` → `RUNNING` in one
    transaction; a lost claim removes the id and leaves the run queued), and dispatches `INGEST` to `IngestPipeline` and
    `LINT` to `LintService`. A finishing run drains again.
  - **Throttling**: every model call of an `INGEST` or `LINT` run takes a permit from one global `Semaphore`
    (`mindforge.ai.background-permits`, default 4); writes fan out on virtual threads within it.
  - **Commit 1** (`@Transactional`, fenced `RUNNING` → `WRITTEN`): pages, revisions, sources, links.
  - **Commit 2** (fenced `WRITTEN` → `COMPLETED`): supersessions, `supersession_count`, lease released.
  - **Failure**, from a `finally`: before or at commit 1, or no page task succeeded → `FAILED` with `failure_reason`,
    `retryable` and `failures`, lease released; Supersede or commit 2 → `COMPLETED` + `supersession_skipped`, lease
    released; a fence that matches 0 rows → `RunFencedException`, WARN, nothing written, released or notified.
  - **Sweep** on `ApplicationReadyEvent` and `@Scheduled(fixedDelay = 60 s)`, over runs not in the active set:
    `WRITTEN` → `COMPLETED` + `supersession_skipped`; `RUNNING` → `FAILED` "interrupted", `retryable`, and an `INGEST`
    run with `attempt < 3` is re-queued as a new run with `attempt + 1`; leases released; every knowledge base with
    `QUEUED` runs drained. Stops claiming on `ContextClosedEvent`. Ceiling: one live instance.
  - **Progress**: `ProgressNotifier` port (best-effort, never throws), called at each step boundary, for write fan-out
    progress, and after each status transaction returns. `SseProgressNotifier` (`dev.mindforge.infrastructure.event`)
    keeps `Map<kbId, Set<SseEmitter>>` and drops dead emitters. The endpoint is 9.7.
  - Record each model service's `VERSION` + model id in `step_versions`. `cost` stays `NULL` until Phase 14 (T28).
  - `UploadSource` gains `CONVERSATION`; the conversation entry point is exposed by Phase 11. With it,
    `DocumentRepository.findByContentHash` excludes `CONVERSATION` rows and conversation turns skip the lesson rule
    (T18, T25) — Phase 4 had no conversation turns to exclude.

- [x] **6.9 — Tests** (`StubAIGateway` fixtures, no real HTTP)
  - No successful page task fails the run; partial success lands and records failures; all-unchanged drafts complete with
    no revisions and no log line.
  - The claims-per-call cap (retryable) and the page-task cap (not retryable) fail loudly.
  - A hallucinated or `sources/` target becomes a create from the claim's title; two claims reaching one path make one
    task and one revision.
  - A chunk can target a page planned by an earlier chunk.
  - Draft validation: an invalid link, an image, frontmatter, `# Citations`, a title heading and a dropped section each
    fail their page task; a `# comment` inside a fence is not a heading.
  - A link insertion that would change prose, target a failed create, or sit inside a fenced block is rejected.
  - Supersession proposals outside the candidates, on a Source Summary, or self-referencing are dropped; omitted
    candidates are recorded.
  - Edits: a `Delete` tombstones a live Concept; a `Delete` of a Source Summary is dropped; an edit naming no page fails
    with `retryable = false`; a `Retitle` changes only the title.
  - Two uploads into one knowledge base serialize; into two they run concurrently; a Lint queued behind an ingest runs
    next.
  - Fencing: a run swept while generating cannot commit.
  - The sweep settles `RUNNING` and `WRITTEN` runs and re-queues an interrupted ingest at most twice.
  - `SseProgressNotifier`: a subscriber receives step messages; a dead emitter is dropped; no subscriber is a no-op.
  - Revert after an ingest restores every page it still tips.
  - `VERSION` constant present on each model service.

### Completion Checklist

- [x] A real document ingests into pages end to end against `StubAIGateway` fixtures.
- [x] No LLM call happens inside a database transaction.
- [x] Pages written are counted from inserted rows, never from model output.
- [x] Every model service declares `static final String VERSION`, recorded on the run.
- [x] No work is lost while the lease is held: every waiting run is `QUEUED`.
- [x] Every commit is fenced on the run's status and the lease.

---

## [x] Phase 7 — Lint

> **Number reused in v3.0.** Phase 7 was the *Neo4j Graph Layer*, deleted by ADR 0016 (the graph is
> `page_links`). Lint occupies the same position in the dependency graph, right after ingest.

**Goal:** Implement live structural health checks and the on-demand full review (ADR 0017). The link
check inside ingest is already built in Phase 6.

> **Changed in v3.1:** health reads through `WikiHealthQuery`, checks dangling supersessions in Java and shows the index
> against its ceiling (T25, T26, T23); duplicate titles are reported for Concepts only (T22); a full Lint is a queued run
> (T17).
> **As built:** `WikiHealthQuery` returns rows, and a wrong-directory link is listed among the dangling links too. An
> orphan is a live Concept no *other* page links to; duplicate titles match exactly. `HealthService` matches
> supersession anchors over `MarkdownStructure` and measures the index with `IndexRenderer.RETRIEVAL_CEILING_TOKENS`;
> the latest full review's findings are read from its run by the 9.7 endpoint. `LintService.request` queues the run
> the endpoint will start, refusing while a `LINT` run is queued or active — a check without a lock, so two racing
> requests can queue two. A Lint has no Supersede, so its one fenced transaction moves `RUNNING → WRITTEN →
> COMPLETED` and stores findings through the new `IngestRunRepository.recordFindings`. Concept bodies go in chunks of
> `chunkSizeTokens`, each read by one `LinkChecker` and one `WikiReviewer` call; either failing is recorded and the
> run goes on. No prefilter exists yet (ADR 0016), so the whole index is sent. A finding or suggestion is stored as
> `{kind, pages, text}`: `contradiction` and `unmarked_supersession` are findings, `missing_page` and `question`
> suggestions; any other kind is dropped and `pages` keeps only live paths. The worker now dispatches `LINT` runs.

### Tasks

- [x] **7.1 — Health** (`WikiHealthQuery` adapter + `HealthService`)
  - SQL: dangling links; wrong-directory links (a dangling link whose final segment matches a live page elsewhere);
    orphan Concepts (no inbound links); duplicate titles among live Concepts.
  - Java over SQL rows: dangling supersessions — the anchor is not a level-1 anchor of the superseded body
    (`MarkdownStructure`), or the superseding page is gone.
  - Index size: `TokenEstimate` of the rendered index against the 20K-token ceiling ("prefilter due" past it).
  - `KnowledgeBaseHealth` record. No run, nothing stored.

- [x] **7.2 — `WikiReviewer`** (LARGE) **and `LintService`**
  - A `LINT` run, inserted `QUEUED` and claimed like any run (6.8); reads live Concept bodies in chunks with the
    (prefiltered) index.
  - Link insertions through `LinkChecker` + `LinkInsertionApplier`; findings (contradictions, unmarked
    supersessions) and suggestions (missing pages, questions to investigate) stored in `ingest_runs.findings`.
  - Never writes prose; never inserts supersessions; never generates a page from a suggestion.

- [x] **7.3 — Tests**
  - Each health query against fixture data; a supersession whose anchor exists only inside a fenced block is reported
    dangling.
  - `LintService` with `StubAIGateway`: insertions applied, prose unchanged, findings stored, queued behind an active run.

### Completion Checklist

- [x] Health checks run without an LLM call or a run.
- [x] A full review's insertions are revertible like any run.
- [x] Lint has no code path that writes a page body other than link insertion.

---

## Phase 8 — Event System *(retired in v3.1)*

> **Retired in v3.1** ([T20](../wayfinder/tickets/20-progress-and-domain-events.md)). Progress is not a domain event, and
> the pipeline needs its notifier from the start. The `ProgressNotifier` port, the in-memory `SseProgressNotifier` and the
> `IngestRunQueued` event are built in Phase 6.8; the progress endpoint in Phase 9.7. The number is retired, as Phase 7's
> Neo4j slot was.
>
> **Design decision** (unchanged, ADR 0015): no transactional outbox and no Redis. Listeners run after commit and
> tolerate a missed event; progress is best-effort.

---

## [x] Phase 9 — API Layer (Spring MVC)

> **Changed in v3.0:** 9.6 and 9.7 — controllers for the wiki, runs, revert and health replace
> `ArtifactController`; documents cannot be deleted individually (ADR 0015).
> **Changed in v3.1:** retry and Lint-start endpoints, synchronous revert and supersession removal, per-knowledge-base
> progress, lesson collisions and busy knowledge-base deletes as 409 (T16, T17, T18, T20, T24).
> **As built:** every document, run and supersession endpoint is nested under `/api/knowledge-bases/{kbId}` — upload
> `POST …/documents` (multipart `file`, `lessonId`, `newVersion`), retry `POST …/documents/{id}/runs`, revert
> `POST …/runs/{id}/revert` — so ownership is checked on the path's knowledge base and no port needs an unscoped id
> lookup. Accounts are `AccountService` over `UserRepository` and a `PasswordHasher` port (BCrypt 12); emails are
> stored lowercased, a provider sign-in joins the account with its email, and a provider that shares no email is
> refused. `JwtService` (jjwt HS256, subject = user id, secret at least 32 bytes) sets the `token` cookie, `Secure`
> unless `AUTH_SECURE_COOKIES=false`; `JwtFilter` is built inside `SecurityConfig`, so it is not also registered as a
> servlet filter, and an anonymous `/api/**` request gets 401. One `NotFoundException` stands in for the page and
> document variants; `NotOwnerException` is the 403; `UploadRejectedException` and `UnknownLessonException` are 422,
> `LintAlreadyQueuedException` and `AccountException` (email taken) 409, a failed sign-in 401. The run report is
> assembled by `RunReportService`: `RunReportQuery` gained `listRuns(kbId, limit)` and `supersessionsOf`, and
> `RevertService` gained `isOffered` and notifies `COMPLETED` after its commit. Failures leave the API only through
> the keys `step`, `item`, `path`, `reason` and `count`; `step_versions` is never mapped. The page view is rendered by
> `PageRenderer` (`application.wiki`, shared with export) from `WikiStore.liveSupersessionsOf` and
> `BundleQuery.citations(kbId, pageIds)`, so `BundleQuery` needs no `supersessionNotes`. Revisions come as one list,
> oldest first, each after its prior revision. The health response carries the latest completed full review, the
> document list each document's latest run (`IngestRunRepository.latestPerDocument`) without conversation turns, and
> `KnowledgeBase` gains a derived `documentCount`. springdoc moves to 3.0.0, the line for Boot 4. API tests drive a
> random port with the JDK `HttpClient`, since MockMvc's auto-configuration is no longer in the default test starter.

**Goal:** Implement the auth system (Google/GitHub OAuth2 + email/password + JWT), all REST controllers,
security config, global exception handler, and SPA serving.

### Tasks

- [x] **9.1 — `MindForgeApplication.java`** (`dev.mindforge`) — `@SpringBootApplication` entry point. No business logic.

- [x] **9.2 — `SecurityConfig.java`** (`dev.mindforge.api.config`)
  - Spring Security filter chain: CSRF disabled for API paths, JWT cookie filter,
    OAuth2 login (Google, GitHub), stateless session for API endpoints.
  - JWT stored in `HttpOnly; Secure; SameSite=Lax` cookie — never in response body.
  - `BCryptPasswordEncoder` with cost 12.

- [x] **9.3 — `JwtFilter.java`** — reads JWT from cookie, validates, sets `SecurityContext`.

- [x] **9.4 — `AuthController.java`** (`dev.mindforge.api.controller`)
  - `POST /api/auth/register`, `POST /api/auth/login` (sets JWT cookie), `POST /api/auth/logout`,
    `GET /api/auth/me` (never includes `passwordHash`). OAuth2 callbacks handled by Spring Security.

- [x] **9.5 — Request/response DTOs** (`dev.mindforge.api.dto`) — all Java `record` types.
  - Never expose: `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion`, `cost`,
    `step_versions`, raw `failures` internals.

- [x] **9.6 — `GlobalExceptionHandler.java`** (`@ControllerAdvice`)
  - `LessonIdentityException` → 422; `PageNotFoundException` / `DocumentNotFoundException` → 404;
    `RevertNotAllowedException`, `KnowledgeBaseBusyException`, `LessonAlreadyExistsException` (with the lesson's id
    and title), `RetryNotAllowedException` → 409; `AccessDeniedException` → 403; `DeadlineExceededException` → 503.
  - Uniform `{ "error": "...", "code": "...", "detail": "..." }` shape. Ingest failures are run
    state, not HTTP errors.

- [x] **9.7 — REST controllers** (`dev.mindforge.api.controller`) — T17, T18, T20, T24, T28
  - `DocumentController`:
    - upload with optional `lessonId` and `newVersion` (202 + document id; 409 on a lesson collision);
    - list, get — with latest-run status and `retryable`;
    - retry `POST /api/documents/{id}/runs` (202 + run id; 409 unless the latest run is `FAILED`).
  - `KnowledgeBaseController`: CRUD for knowledge bases; document and page counts derived at read; delete cascades
    everything and is 409 while a run is active (`DELETE … WHERE active_run_id IS NULL`).
  - `WikiController`: rendered index; page by path (body with supersession notes and citations); page revisions, each
    with its prior revision (the SPA diffs them); the graph (live pages + `page_links`).
  - `RunController`:
    - list runs `GET /api/knowledge-bases/{kbId}/runs`;
    - run report (pages written with prior revisions and body-length deltas, supersessions, failures);
    - revert `POST /api/runs/{id}/revert` — synchronous; 409 when busy or not offered;
    - remove one supersession `DELETE /api/knowledge-bases/{kbId}/supersessions/{id}` — synchronous; 409 when busy;
    - progress `GET /api/knowledge-bases/{kbId}/progress` — SSE, every run of the knowledge base (T20).
  - `HealthController`: `GET /api/knowledge-bases/{kbId}/health`; start a full Lint
    `POST /api/knowledge-bases/{kbId}/lint-runs` (202; 409 if a `LINT` run is already queued or active).
  - `UserController`: profile management.
  - Every method thin: validation + ownership check + delegate. Constructor injection only.

- [x] **9.8 — SPA serving**
  - Static Angular build served from classpath under `/static/`; non-API paths fall through to `index.html`.

- [x] **9.9 — API integration tests** (`integration/api/`)
  - Auth flow: register → login → access protected resource → logout.
  - Upload flow: upload → run completes (stub gateway) → pages listed → no sensitive fields in any response.
  - Upload with an existing lesson id → 409; the same with `newVersion` → 202.
  - Deleting a knowledge base while a run is active → 409; a queued upload and a retry both run in order.
  - Ownership: user A cannot read user B's pages, runs, progress stream or health (403).

### Completion Checklist

- [x] All controllers thin — no business logic; all delegated to application services.
- [x] JWT stored in `HttpOnly` cookie only — never in response body.
- [x] No sensitive fields in any API response (verified by integration tests).
- [x] Ownership check present on every `@RestController` method.

---

## [x] Phase 9b — Bundle Export

> **New in v3.0.** Decided in [T11](../wayfinder/tickets/11-bundle-export.md).

**Goal:** Download a knowledge base as a conformant OKF bundle.

> **Changed in v3.1:** reads through `BundleQuery`, escapes titles, re-worded `log.md` rule, `timestamp` source and a
> never-empty filename (T19, T25).
> **As built:** `BundleExporter.export(kbId, name)` renders inside its `REPEATABLE_READ` read-only transaction and
> validates before returning, throwing `BundleNotConformantException` (logged, 500); the endpoint only streams the
> already-validated files through `writeZip`. The index is rendered from the same page rows as the files, so it can
> never list a page the zip lacks. Supersession notes come from `WikiStore.liveSupersessionsOf` and citations from
> `BundleQuery.citations`, both through Phase 9's `PageRenderer`. Frontmatter is dumped by SnakeYAML in block style
> with no line wrapping; `timestamp` is the ISO instant of `wiki_pages.updated_at`. The zip's root directory is
> `<slug>-okf`, the filename without `.zip`. The validator caught a real defect while being built: a lesson title
> stored with a newline broke a `log.md` line, so `LogRenderer` and citations now flatten lesson titles with
> `TextRules.singleLine` too. Study tables do not exist yet; the no-study-data test seeds run failures, findings, step
> versions and cost instead, and Phase 10 extends it to cards and sessions.

### Tasks

- [x] **9b.1 — `BundleExporter`** (`dev.mindforge.infrastructure.export`)
  - One `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` snapshot; no lease.
  - Per page: SnakeYAML frontmatter (`type`, `title`, `description`, `timestamp` = `wiki_pages.updated_at`) + stored
    body verbatim + supersession notes as a blockquote under the superseded level-1 heading
    (`MarkdownStructure`) + projected `# Citations`. Titles in link text escape `\`, `[` and `]`.
  - `index.md` from `IndexRenderer`; `log.md` from `LogRenderer`.
  - Reads through `WikiStore.listBodies`, `BundleQuery` (citations, supersession notes) and
    `RunReportQuery.logEntries` — never study tables, `cost`, `step_versions`, `failures` or `findings`.

- [x] **9b.2 — `BundleConformanceValidator`** (pure function over rendered files)
  - OKF §9 rule 1: every non-reserved `.md` has parseable YAML frontmatter. Rule 2: non-empty `type`.
  - Rule 3, re-worded (T19): `index.md` has optional frontmatter holding only `okf_version`, then `# ` sections each
    followed by zero or more `* [title](url)` lines optionally ending ` - description`; `log.md` has one `# ` heading,
    then `## YYYY-MM-DD` headings each followed by one or more `* ` lines; blank lines anywhere.
  - Rule 4: every path is `(concepts|sources)/` + a name matching `Identifier.PATTERN`, not reserved.
  - A violation is a 500 and is logged — never a shipped bundle.

- [x] **9b.3 — Endpoint**
  - `GET /api/knowledge-bases/{kbId}/export` → `application/zip`, `Content-Disposition: attachment;
    filename="<Identifier.slugify(kb name)>-okf.zip"` (never empty), the zip's root directory named the same; streamed
    via `StreamingResponseBody`; ownership checked.

- [x] **9b.4 — Tests**
  - Validator as oracle over exporter output; YAML escaping of titles with `: ` and quotes; a title containing `]` and
    a lesson title that arrived with a newline still validate; an empty knowledge base exports a valid bundle; a
    concurrent ingest commit does not tear the bundle; no study data in any file.

### Completion Checklist

- [x] Every exported bundle passes the conformance validator.
- [x] No new dependency added.

---

## [x] Phase 10 — Quiz and Flashcard Services

> **Changed in v3.0.** Study material is cut from Concept pages (ADR 0018). `FlashcardGenerator`,
> `QuizGenerator` and `QuizEvaluator` arrive here as model services, not Phase 6 agents. Graph RAG
> targeting is replaced by weak-page SQL.
> **As built:** as in Phase 9, the study endpoints are nested under the knowledge base —
> `GET /api/knowledge-bases/{kbId}/flashcards?lessonId&pageId`, `POST …/flashcards/{cardId}/reviews`,
> `POST …/quiz-sessions`, `GET …/quiz-sessions/{id}/next` (204 once the quiz is over) and `POST …/{id}/answers`
> (409 `QUIZ_FINISHED` after the last). `StudyProgressStore.replaceCards` inserts `ON CONFLICT DO NOTHING`, gives a
> returned existing card its new anchor and hash (reviving a retired one due now) and retires the rest;
> `dueCards` inner-joins `wiki_pages` and skips sections with a live supersession; `pageScores` is a window over each
> page's last five events. `QuizSessionStore` has `insert`, `find` (unexpired), `updateCursor` and the cleanup's
> system method `deleteExpired` (every 15 minutes); its adapter keeps a Caffeine cache in front of PostgreSQL, and
> sessions live `mindforge.study.quiz-session-ttl` (2 h). `SM2Scheduler` sits in `application.study` beside
> `StudyPages`, which resolves a scope (the `conversation` lesson is a 404) and strips superseded sections through
> `PageRenderer.withoutSections`; the interval grows by the updated ease, and a failed recall is due tomorrow. A
> page's lock is dropped from the map when no thread waits on it; a generation failure is logged and the page keeps
> its cards. Model output is checked in code: a card needs a known type, a front and a back, and an anchor that is not
> one of the page's sections becomes null; a question must name a page it was shown. A quiz asks five questions over
> the targeted pages and their neighbours, within `chunkSizeTokens`. Study model services call the gateway directly,
> outside the background permit pool. The `CHAR(16)` columns map with `@JdbcTypeCode(SqlTypes.CHAR)`.

**Goal:** Implement flashcards with SM-2 that survive page rewrites, and server-authoritative quizzes
targeting weak pages.

### Tasks

- [x] **10.1 — Domain types** (`dev.mindforge.domain.model`)
  - `ReviewResult` record: `int rating` (0–5).
  - `Flashcard(cardId, pageId, sectionAnchor, cardType, front, back, sourceHash)`;
    `cardId = sha256(kbId|pageId|cardType|front|back)[:16]`;
    `sourceHash = sha256(title + "\n" + stripLinks(body))[:16]` of the page the card was generated or reused from (T21).
  - `StudyScope` sealed interface: `WholeKnowledgeBase`, `Lesson(lessonId)` (pages via `WikiStore.pageIdsForLesson`;
    the reserved `conversation` lesson is never offered), `Page(pageId)`.
  - Ports: `StudyProgressStore` (due cards, save review, weak pages), `QuizSessionStore` — `kbId` first.
  - `ProcessingSettings.cardPagesPerSession` (default 10) — one generation budget for new and stale pages (T27).
  - Weakness: a page's score is the mean of its last 5 `study_events`; weak below 3.0.

- [x] **10.2 — Migration `V3__create_study.sql` and stores**
  - `flashcards` (PK `(knowledge_base_id, card_id)`, `page_id` without FK to `wiki_pages`, `section_anchor`,
    `source_hash CHAR(16)`, `retired_at`, SM-2 columns). Inserts are `ON CONFLICT (knowledge_base_id, card_id) DO NOTHING`.
  - `study_events (knowledge_base_id, page_id, card_id, kind CARD|QUIZ, score, occurred_at)`.
  - `quiz_sessions` — questions JSONB with reference answers and grounding; TTL via `@Scheduled` cleanup;
    Caffeine wraps the PostgreSQL store.

- [x] **10.3 — SM-2 algorithm** (`dev.mindforge.application.service`)
  - `SM2Scheduler` pure Java class. `EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))`, EF ≥ 1.3.

- [x] **10.4 — `FlashcardGenerator`** (LARGE) **and `FlashcardService`** — ADR 0018, T21, T27
  - Stale: the page's current hash differs from its cards' `source_hash`. A link-only or supersession-only change is
    never stale.
  - Lazy: when a deck opens for a scope, spend at most `cardPagesPerSession` generation calls — stale pages with due
    cards (oldest due first), then Concept pages without cards (by `created_at`). Calls run in parallel on virtual
    threads with `DeadlineProfile.BATCH`; the request waits for them. Stale pages past the budget keep serving their cards.
  - Per-page `ReentrantLock` in a `ConcurrentHashMap` (not `synchronized`); staleness re-checked after acquiring it.
  - The generator receives the page with superseded sections stripped, its current cards, and its retired cards whose
    `source_hash` equals the current hash, for verbatim reuse.
  - Returned cards are stamped with the current `source_hash` and their `section_anchor`; a returned id that was retired
    is un-retired with its SM-2 history and `due_at = now`; cards not returned get `retired_at`.
  - Due query inner-joins `wiki_pages` and excludes cards whose `(page_id, section_anchor)` has a live supersession.
  - `reviewCard` updates SM-2 and appends a `study_events` row.

- [x] **10.5 — `QuizGenerator`** (LARGE), **`QuizEvaluator`** (SMALL) **and `QuizService`** — T27
  - `startSession(kbId, userId, scope)`: whole-knowledge-base order is weak pages by ascending mean, then unstudied
    Concepts by `created_at`, then the rest by ascending mean; then 1-hop `page_links` neighbours; one generation call
    with superseded sections stripped; batch stored in the session.
  - `nextQuestion`: question text only. `submitAnswer`: grade against the session's reference answer and
    grounding excerpt on SM-2's 0–5 rubric (clamped); return score + feedback; append `study_events`.

- [x] **10.6 — Controllers**
  - `QuizController`: `POST /api/quiz/sessions`, `GET /api/quiz/sessions/{id}/next`, `POST /api/quiz/sessions/{id}/answers`.
  - `FlashcardController`: `GET /api/flashcards?kbId&scope`, `POST /api/flashcards/{id}/reviews`.

- [x] **10.7 — Tests**
  - `SM2SchedulerTest`: rating 5 → EF increases; rating 0 → reset; EF never below 1.3.
  - `FlashcardServiceTest`: a revision keeps unchanged cards' history; a changed answer retires the old card;
    revert revives it with history and `due_at = now`; a link-only revision regenerates nothing; superseded sections
    are skipped; the generation budget covers new and stale pages together; two concurrent deck opens make one
    generation call per page; a reused card's `section_anchor` follows its section.
  - `QuizServiceTest` with `StubAIGateway`: no reference answer in responses; weak pages targeted first; a knowledge base
    with no study events orders by creation.

### Completion Checklist

- [x] SM-2 produces correct scheduling for all ratings (0–5).
- [x] Quiz responses contain no `referenceAnswer` or `groundingContext`.
- [x] Flashcards are never written into a page and never exported.
- [x] Due cards are always scoped to `kbId`.

---

## [x] Phase 11 — Query

> **Re-cut in v3.0.** This phase was *Search and Conversational RAG* over `content_embeddings`. Query
> reads curated pages chosen from the rendered index instead (ADR 0016). pgvector is gone.
> **As built:** the endpoints are nested under the knowledge base like every other —
> `POST /api/knowledge-bases/{kbId}/query-sessions`, `GET …/query-sessions` (the user's history, question and answer
> only), `POST …/{id}/messages`, `POST …/{id}/edits` (202 with the run id) and `GET …/pages/search?q=`.
> `InteractionStore` also has `turns(kbId, interactionId)`, which feeds the last five turns to both prompts;
> `listForUser` selects the two redacted columns in the query itself. Page search is the `PageSearchQuery` port —
> escaped `ILIKE` on title and description plus `simple` full-text on bodies, title matches first, nothing for a
> query under two characters — and no `pg_trgm` yet. `QueryService` lets `PageSelector` pick at most eight paths,
> keeps the live ones in its order, then their one-hop outbound neighbours, rendered with supersession notes into a
> `TokenBudget(24 000, 4 000)`; context stops at the first page that does not fit, and citations keep only paths the
> writer was given. Both calls use `DeadlineProfile.INTERACTIVE` and the gateway directly. A conversation edit is
> `IngestionService.submitEdit`, inserting the `CONVERSATION` document and its `QUEUED` run under the knowledge-base
> lock, and needs the chat session to be the user's; the chat message for "edit named no page" and the inline run
> report belong to the SPA (Phase 12), and "undo" is the run's revert endpoint.

**Goal:** Answer multi-turn questions from the wiki with page citations; accept conversation edits to the
wiki; provide page search for the SPA.

### Tasks

- [x] **11.1 — Domain types and migration**
  - `Interaction(interactionId, userId, kbId, startedAt)`;
    `InteractionTurn(turnId, question, answer, usedPagePaths, createdAt)`;
    `TokenBudget(totalTokens, reservedForResponse)` with `availableForContext()`, counting with `TokenEstimate` (T23).
  - `InteractionStore` port, `kbId` first: `createInteraction`, `addTurn`, `getInteraction`, `listForUser` (redacted).
    No `listUnredacted` — there is no admin role (T28).
  - Migration `V4__create_interactions.sql`: `interactions` and `interaction_turns`, both with `knowledge_base_id` FK
    `ON DELETE CASCADE`; `interaction_turns.used_page_paths TEXT[]` (T28).

- [x] **11.2 — `PageSelector`** (SMALL), **`AnswerWriter`** (LARGE) **and `QueryService`**
  - Select: rendered index (prefiltered past 20K tokens) + question + prior turns → page paths.
  - Load: verify live paths; bodies with supersession notes; add 1-hop link neighbours while `TokenBudget` allows.
  - Answer: `DeadlineProfile.INTERACTIVE`; cites page paths. Query never writes pages and takes no lease.

- [x] **11.3 — Conversation edits** — ADR 0012, T15
  - An explicit *edit* action in chat (not a question) is persisted as a `CONVERSATION` `Document` — lesson id
    `conversation`, lesson title `Conversation`, `text/plain`, filename `conversation`, content hash over the UTF-8 content —
    together with a `QUEUED` `INGEST` run, exactly like an upload. "Save that" stores the instruction followed by the
    quoted answer.
  - The run goes through Extract's edit prompt (6.3) — claims, `Delete`, `Retitle` — then Resolve; RelevanceGuard and the
    Source Summary task are skipped. Deletion and retitling have no other channel.
  - The user sees the run report inline. An edit that named no page fails (`retryable = false`) and chat says *"I couldn't
    tell which page to change — name the page or rephrase."*; dropped items are listed with their reasons. "Undo" calls
    `RevertService` (409 while a run is active).

- [x] **11.4 — Page search** (`SearchService`)
  - Title/description match and `simple` full-text over `wiki_pages` bodies, scoped to `kbId`.
  - Shares the lexical code the index prefilter will use; `pg_trgm` is added only when a knowledge base passes the ceiling.

- [x] **11.5 — `InteractionStoreAdapter`** (PostgreSQL)
  - `listForUser(kbId, userId)` returns only `question` + `answer`.

- [x] **11.6 — Controllers**
  - `QueryController`: `POST /api/query/sessions`, `POST /api/query/sessions/{id}/messages`,
    `POST /api/query/sessions/{id}/edits`.
  - `SearchController`: `GET /api/knowledge-bases/{kbId}/pages/search?q=...`.

- [x] **11.7 — Tests**
  - `QueryServiceTest` with `StubAIGateway`: hallucinated paths dropped; `TokenBudget` trimming; no grounding in responses.
  - Conversation edit creates a `CONVERSATION` document (lesson `conversation`) and a `QUEUED` run; an identical edit a
    week later is not deduplicated; "delete Mitoza" tombstones the page; an edit naming no page fails with the chat
    message above; the `conversation` lesson never appears in the study scope picker.
  - `InteractionStoreAdapterTest`: `listForUser` redacts internals.

### Completion Checklist

- [x] Query answers cite pages, never uploads directly.
- [x] Query has no write path; conversation edits are ingest runs.
- [x] Responses never include `groundingContext`, `rawPrompt` or `rawCompletion`.

---

## [x] Phase 12 — Angular Frontend

> **Changed in v3.0:** the document-centric summary/flashcards/concept-map tabs are replaced by wiki,
> run, health, study and chat surfaces.
> **As built:** scaffolded with the Angular 21 CLI (zoneless, `app.ts` file naming, no spec files); UI text is
> Polish. Every knowledge-base route is a child of `/kb/:kbId`, whose shell holds the tabs and the export link and
> provides one `ProgressService` (one `EventSource` per open knowledge base) to its pages, which read route params
> through input binding with params inheritance. A page's route is `/kb/:kbId/pages/:directory/:name`; its "study
> this page" link opens the study view with the page scope, since index entries carry no page id. API types are
> generated by `openapi-typescript` into `core/models/api.generated.ts` (`npm run openapi:generate`) and aliased in
> `api.ts`; the SSE `RunProgress` payload is the one hand-written type, because an event body has no schema. For
> the generated types to be exact, the backend's `ApiDocsConfig` publishes `ErrorResponse` and marks every response
> record's properties required. Bodies render through `marked` with raw HTML escaped and images reduced to their alt
> text; page links open inside the SPA, external links get `rel="noopener noreferrer nofollow"`, and a fragment
> opens its page at the top (T11). `mvn package -DskipFrontend=false` runs `npm ci` and the build through
> `frontend-maven-plugin` (Node 22.12, npm 11.9), and `maven-resources-plugin` copies the build into
> `target/classes/static` rather than `src/main/resources/static`, so no build output enters the source tree.

**Goal:** Create the Angular SPA with standalone components, lazy-loaded routing, auth integration and
all user-facing pages for the learning loop.

### Tasks

- [x] **12.1 — Angular project setup**
  - `ng new frontend --standalone --routing --style=scss` inside `frontend/`.
  - Install: `@angular/material`, `cytoscape`, `@types/cytoscape`, `diff` (revision diffs are computed in the SPA, T28).
    Proxy `/api` to `:8080`.

- [x] **12.2 — Core infrastructure**
  - `AuthService` (JWT cookie, Google/GitHub OAuth2), `ApiService` (generated types), `AuthGuard`,
    global `HttpInterceptor` mapping 4xx/5xx to toasts.

- [x] **12.3 — Pages**
  - `/login`; `/dashboard` — knowledge bases.
  - `/kb/:id` — documents, upload dropzone (a 409 lesson collision asks *new version of "…"* or *new lesson* with an
    editable id), recent runs with live SSE progress, a retry button on retryable failed runs.
  - `/kb/:id/pages` — the index, grouped Concepts / Sources, with page search.
  - `/kb/:id/pages/:path` — a page with supersession notes, citations and revision history. Bodies render with raw HTML
    escaped; external links get `rel="noopener noreferrer nofollow"`.
  - `/kb/:id/graph` — Cytoscape.js graph of pages and `page_links`.
  - `/kb/:id/runs/:runId` — run report: pages written with diffs and body-length deltas, supersessions with per-row
    removal, failures and omissions, revert (a 409 says the knowledge base is busy).
  - `/kb/:id/health` — live findings, index size against the ceiling, latest full review's findings and suggestions,
    "run full review".
  - `/kb/:id/study` — scope picker (the `conversation` lesson is not offered), flashcard review, quiz.
  - `/kb/:id/chat` — Query conversation with an explicit edit action and inline run reports.
  - Export button on `/kb/:id`.

- [x] **12.4 — Real-time progress** — T20
  - `EventSourceService` subscribes to `GET /api/knowledge-bases/{kbId}/progress` while a knowledge base is open; on
    (re)connect it first reads `GET /api/knowledge-bases/{kbId}/runs`, then applies `RunProgress` messages — status
    changes and a step stepper — for every run of that knowledge base.

- [x] **12.5 — Build integration**
  - `npm run build` output lands in `frontend/dist/frontend/browser/`; `frontend-maven-plugin` copies it into
    `src/main/resources/static/` during `mvn package`.

### Completion Checklist

- [x] `npm start` serves the SPA on `:4200` with proxy to `:8080`; `mvn package` embeds the build.
- [x] All routes navigable; lazy loading confirmed.
- [x] Revert is reachable from every run report while it is offered.
- [x] API models match backend DTOs (generated).

---

## [x] Phase 13 — Docker and Deployment

> **Changed in v3.0:** no Neo4j service; plain PostgreSQL.
> **As built:** `spring-boot-starter-actuator` provides the health check, served at `/health` by
> `management.endpoints.web.base-path: /`. The SPA stage uses `node:22-alpine` (Angular 21's supported line); the
> runtime runs as a non-root user. `compose.yml` fills the OAuth client variables with placeholders, because Spring
> Security refuses an empty client id, and passes `JWT_SECRET` through: Compose interpolates each file before merging,
> so a hard requirement there would defeat the override, and the app refuses to start without a secret anyway.
> `compose.override.yml` supplies a development secret, publishes PostgreSQL and turns off `Secure` cookies for local
> HTTP. `server.port` follows `PORT`, and shutdown is graceful with a 30 s phase
> timeout. Railway runs one replica with `/health` as its check; `docs/project/deployment.md` is the procedure.
> The smoke test built the image, brought both services up healthy, and got 200 from `/health` and the SPA's
> `index.html` from `/`.

**Goal:** Complete Docker multi-stage build, Docker Compose for local and production, and Railway/Render
deployment configuration.

### Tasks

- [x] **13.1 — Multi-stage `Dockerfile`**
  - Stage 1 (`node:20-alpine`): `npm ci && npm run build` in `frontend/`.
  - Stage 2 (`maven:3.9-eclipse-temurin-21`): `mvn package -DskipTests`.
  - Stage 3 (`eclipse-temurin:21-jre-alpine`): `COPY --from=2 target/*.jar app.jar`; `EXPOSE 8080`.

- [x] **13.2 — `compose.yml`**
  - Services: `app`, `postgres` (PostgreSQL 15). Health checks on both; `app` depends on `postgres` health.
  - Volume mount for PostgreSQL data. `compose.override.yml` for local dev.

- [x] **13.3 — Deployment configuration**
  - `railway.json` (or `render.yaml`); `Procfile` fallback; `docs/project/deployment.md`.
  - Single live instance: the run sweep treats runs outside its own process as abandoned. During a deploy's overlap,
    fenced commits keep that correct and in-flight runs re-run once (T17); two permanent instances need a heartbeat lease.
  - Graceful shutdown enabled so the worker stops claiming on `ContextClosedEvent`.

- [x] **13.4 — Smoke test**
  - `docker build -t mindforge .`; `docker compose up`; `curl /health` → 200; `curl /` → SPA index.

### Completion Checklist

- [x] `docker build` creates a working multi-stage image.
- [x] `docker compose up` starts both services with passing health checks.
- [x] Application is deployable to Railway/Render via documented procedure.

---

## [ ] Phase 14 — Observability and Tracing

> **Changed in v3.0:** traces and cost are per ingest run rather than per document pipeline.

**Goal:** Langfuse tracing for LLM calls, per-run cost accounting, and cost anomaly warnings.

### Tasks

- [ ] **14.1 — Langfuse integration** (`dev.mindforge.infrastructure.ai`)
  - Wrap `AIGatewayAdapter` to emit a span per `complete()` call: `modelTier`, `model`, tokens, `costUsd`, `latencyMs`.
  - Configured via `LANGFUSE_SECRET_KEY` + `LANGFUSE_PUBLIC_KEY`; graceful no-op when absent.

- [ ] **14.2 — Run tracing**
  - `IngestPipeline`, `LintService` and `RevertService` emit a trace per run with child spans per step;
    trace id recorded alongside `step_versions`.
  - Populate `CompletionResult.costUsd` and write the summed `ingest_runs.cost` — `NULL` until this phase, meaning
    "not measured" (T28).

- [ ] **14.3 — Cost anomaly warnings**
  - If a run's `cost` exceeds a configurable threshold (default $0.50), log `WARN` with run id and per-step breakdown.

### Completion Checklist

- [ ] Langfuse traces appear for a real ingest run.
- [ ] Per-run cost matches the sum of its calls.
- [ ] Integration gracefully disabled when env vars are absent.

---

## [ ] Phase 15 — CLI Entry Points

> **Changed in v3.0:** `mindforge-backfill` (Neo4j rebuild) is deleted.

**Goal:** Utility CLI entry points for scripted ingestion and terminal quizzes.

### Tasks

- [ ] **15.1 — `mindforge-pipeline` CLI** (`dev.mindforge.cli`)
  - `CommandLineRunner` under `--spring.profiles.active=cli`; `--file <path> --kb <id>`; ingests through the
    same `IngestionService` and pipeline; prints step progress.

- [ ] **15.2 — `mindforge-quiz` CLI**
  - Interactive terminal quiz through the same `QuizService` as the web UI.

### Completion Checklist

- [ ] Both CLI runners work via `java -jar mindforge.jar --spring.profiles.active=cli`.
- [ ] CLI quiz submits answers through the same `QuizService` path as the web UI.

---

## [ ] Phase 16 — Image Analysis

> **Changed in v3.0:** image descriptions become text blocks that flow into pages like any other content;
> there is no `DocumentArtifact` to attach them to and no checkpoint skip.

**Goal:** Describe images extracted from uploaded PDFs and DOCX files with the VISION tier so their
content reaches the wiki.

### Tasks

- [ ] **16.1 — PDF/DOCX image extraction**
  - Extend `PdfParser` and `DocxParser` to emit `ContentBlock`s with `BlockType.IMAGE` carrying bytes + MIME type.

- [ ] **16.2 — `ImageDescriber`** (`dev.mindforge.agent`, VISION)
  - `VERSION` constant. For each IMAGE block, `AIGateway.complete(VISION, ...)`; replaces the block with a
    TEXT block holding the description, in document order, before `RelevanceGuard`.

- [ ] **16.3 — Unit tests**
  - `ImageDescriberTest` with `StubAIGateway(VISION)`; `PdfParserTest` image extraction.

### Completion Checklist

- [ ] `VISION` tier routes to the correct model string.
- [ ] An image's description appears in the pages its document produced.

---

## [ ] Phase 17 — Article Fetcher

> **Changed in v3.0:** a fetched article is a source of its own — a `Document` ingested as its own run —
> so it gets dedup, provenance and revert unchanged.

**Goal:** Optionally fetch external articles referenced in uploaded documents, safely, and ingest them.

### Tasks

- [ ] **17.1 — `EgressPolicy`** (`dev.mindforge.infrastructure.security`)
  - Allowlist-based URL validator; rejects private IP ranges, `file://`, `data:`.

- [ ] **17.2 — `ArticleFetcher`** (no LLM)
  - Extracts URLs from a document's blocks; validates via `EgressPolicy`; fetches with a timeout; HTML → text.
  - Each article becomes a `Document` (`UploadSource.ARTICLE`, lesson identity from the article title) in the
    same knowledge base, with a `QUEUED` run like any upload. An article whose lesson id already exists is skipped and
    logged — nobody is there to confirm a new version (T18).
  - Third-party content is untrusted (T28): an `ARTICLE` run is create-only — Resolve drops claims that resolve to a
    live page — and Supersede does not run for it (`supersession_skipped`, reason `"article"`).
  - Disabled unless `ProcessingSettings.fetchExternalArticles` is true.

- [ ] **17.3 — Unit tests**
  - `EgressPolicyTest`; `ArticleFetcherTest` with a mock HTTP server, including a policy violation.
  - An `ARTICLE` run whose claims target a live page creates nothing there and inserts no supersession.

### Completion Checklist

- [ ] All outbound HTTP goes through `EgressPolicy`.
- [ ] Article fetching is disabled by default.

---

## [ ] Phase 18 — Discord Bot

> **Changed in v3.0:** `/ask` (Query) replaces `/search`.

**Goal:** Discord bot with ask, quiz and upload slash commands; guild allowlists; identity resolution;
SR reminders via DM.

### Tasks

- [ ] **18.1 — Domain additions**
  - `ExternalIdentityRepository`: `findUserId(platform, externalId)`, `link(...)`, `createUserAndLink(...)`.
  - `UploadSource.DISCORD`.

- [ ] **18.2 — `DiscordBot`** (`dev.mindforge.discord`)
  - JDA as a Spring-managed `ApplicationRunner`. Slash commands: `/ask`, `/quiz start`, `/quiz answer`, `/upload`.
  - Guild and role allowlist via `AppProperties.discord.*`; identity resolution auto-provisions on first contact.

- [ ] **18.3 — SR reminder job**
  - `@Scheduled` daily: DM each Discord-linked user with due cards.

- [ ] **18.4 — Unit tests**
  - First contact auto-provisions; non-allowed guilds rejected; user A cannot reach user B's sessions.

### Completion Checklist

- [ ] Commands delegate to the same application services as the web UI.
- [ ] SR reminder DMs sent to users with due cards.

---

## [ ] Phase 19 — Slack Bot

> **Changed in v3.0:** `/mf-ask` (Query) replaces `/mf-search`.

**Goal:** Slack bot using Slack Bolt for Java with ask, quiz and upload handlers; workspace security;
identity resolution.

### Tasks

- [ ] **19.1 — Add `UploadSource.SLACK`**.
- [ ] **19.2 — `SlackBot`** (`dev.mindforge.slack`)
  - Socket Mode; commands `/mf-ask`, `/mf-quiz`, file upload handler; workspace allowlist; identity via
    `ExternalIdentityRepository`.
- [ ] **19.3 — Unit tests**: allowlist enforcement, identity resolution, ownership check.

### Completion Checklist

- [ ] Bot connects via Socket Mode and responds to commands.
- [ ] Identity resolution reuses the same `ExternalIdentityRepository` as Discord.

---

## [ ] Phase 20 — Security Hardening

**Goal:** Systematic security checklist pass against `web-security.md`, OWASP dependency audit, and rate limiting.

### Tasks

- [ ] **20.1 — Security checklist pass**
  - Verify each requirement in `docs/standards/security/web-security.md`, including: the exporter never
    reads study tables or run internals; every `WikiStore` query binds `kbId`; dedup is per knowledge base.
  - Fix any gaps found.

- [ ] **20.2 — OWASP dependency check**
  - `dependency-check-maven`; fix or suppress any `CVSS >= 7.0` findings.

- [ ] **20.3 — Rate limiting**
  - Caffeine-backed limiter on `/api/auth/login` and `/api/auth/register`; 429 with `Retry-After`.

- [ ] **20.4 — Security integration tests**
  - Rate limit triggers; tampered JWT → 401; cross-user access to pages, runs, export → 403.

### Completion Checklist

- [ ] Every `web-security.md` requirement traced to implementing code.
- [ ] `mvn dependency-check:check` passes.
- [ ] Rate limiter active on auth endpoints.

---

## [ ] Phase 21 — End-to-End Testing and CI/CD

> **Changed in v3.0:** the E2E journey follows the wiki; ArchUnit gains model-service rules.

**Goal:** Full E2E test suite covering core user journeys, GitHub Actions CI pipeline, and quality gates.

### Tasks

- [ ] **21.1 — GitHub Actions workflow** (`.github/workflows/ci.yml`)
  - Push to `main`, PRs: Checkstyle → SpotBugs → `mvn test` (Testcontainers) → JaCoCo 70% gate.

- [ ] **21.2 — Playwright E2E smoke tests** (`src/test/e2e/`)
  - Against `docker compose up` with a stub-provider profile: register → upload → run completes → pages
    appear → open a page → review flashcards → take a quiz → export bundle (validates).

- [ ] **21.3 — Architecture fitness functions** (ArchUnit)
  - No `dev.mindforge.domain` class imports from `infrastructure` or `api`.
  - No `@RestController` method accesses a repository directly.
  - No class in `dev.mindforge.agent` depends on another class in `dev.mindforge.agent`.
  - Only `RunWorker` calls `IngestRunRepository.findUnfinished` and `knowledgeBasesWithQueuedRuns` (T25).
  - No class outside `dev.mindforge.domain.model.MarkdownStructure` matches heading or link syntax with a regex
    (reviewed by a name rule on `Pattern` constants mentioning `#` or `](`) (T26).

### Completion Checklist

- [ ] CI passes on a clean checkout with real Testcontainers.
- [ ] JaCoCo gate blocks PRs below 70%.
- [ ] E2E covers upload → wiki → study → export.
- [ ] ArchUnit enforces layer and model-service boundaries.

---

## Dependency Graph

```
Phase 0 ──► 1 ──► 2 ──► 2b ──► 3 ──► 3b (cleanup)
                                       │
                                  Phase 4  (parsing, upload, dedup, lesson rule)
                                       │
                                  Phase 5  (wiki domain & store, runs, lease, revert)
                                       │
                                  Phase 6  (ingest pipeline, run queue, progress — absorbs retired Phase 8)
                                       │
                                  Phase 7  (Lint) ──► Phase 9 (API) ──► Phase 9b (export)
                                                           │
                                          Phase 10 (study) ─┤─ Phase 11 (Query)
                                                           │
                                                      Phase 12 (SPA)
                                                           │
                                                      Phase 13 (DEPLOY — core system complete)
                                                           │
                              ┌────────────────────────────┼─────────────────────┐
                         Phase 14                     Phase 15              Phase 16
                              │
                         Phase 17 ──► Phase 18 ──► Phase 19
                              │
                         Phase 20 ──► Phase 21
```
