# MindForge — Implementation Plan

> **Version:** 3.0 — wiki re-cut
> **Date:** 2026-09-10
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
10. [Phase 8 — Event System](#phase-8--event-system) *(changed)*
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

## [ ] Phase 2b — Persistence Cleanup & DTO Foundation

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
- [ ] GET /v3/api-docs returns a valid OpenAPI 3.1 JSON document

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

**Goal:** Remove everything Phases 1–3 built for the per-document artifact model, fix the two
defects the re-cut exposed, and squash the migration chain into a baseline — ending green.

### Tasks

- [ ] **3b.1 — Delete domain types** (`dev.mindforge.domain.model`)
  - `Agent`, `AgentCapability`, `AgentContext`, `AgentResult` (ADR 0013).
  - `DocumentArtifact`, `SummaryData`, `ConceptMapData` (ADR 0010).
  - `StepCheckpoint`, `StepFingerprint`, `DocumentStatus` (ADR 0015).
  - `FlashcardData` — Phase 10 writes `Flashcard` keyed on `pageId` (ADR 0018).

- [ ] **3b.2 — Change domain types**
  - `Document`: drop `status`.
  - `DomainEvent`: keep `DocumentIngested`; delete `PipelineStepCompleted`, `ProcessingCompleted`,
    `ProcessingFailed`, `GraphProjectionUpdated`.
  - `LessonIdentity`: transliterating `slugify` (NFD, strip combining marks, `ł→l`, `ø→o`, `ß→ss`,
    `đ→d`, `[a-z0-9-]`); reserve `log` alongside `index` and `default`.

- [ ] **3b.3 — Ports** (`dev.mindforge.domain.port`)
  - Delete `ArtifactRepository`, `GraphIndexer`.
  - `AIGateway`: delete `embed` (ADR 0016).
  - `DocumentRepository`: `findByContentHash(UUID knowledgeBaseId, ContentHash hash)` — today's
    unscoped lookup deduplicates across tenants; delete `updateStatus`.

- [ ] **3b.4 — Infrastructure**
  - Delete `ArtifactEntity`, `StepCheckpointEntity`, `ContentEmbeddingEntity`, `ArtifactJpaRepository`,
    `StepCheckpointJpaRepository`, `ArtifactEntityMapper`, `ArtifactRepositoryAdapter`.
  - `AIGatewayAdapter` and `AiConfig`: drop `EmbeddingModel`.
  - `PersistenceConfig`: drop the `ArtifactRepository` bean.
  - `DocumentEntity`, `DocumentJpaRepository`, `DocumentEntityMapper`, `DocumentRepositoryAdapter`:
    no status; hash lookup scoped by knowledge base.

- [ ] **3b.5 — API DTOs**
  - Delete `ArtifactResponse`, `ArtifactDtoMapper`.
  - `DocumentResponse`, `DocumentDtoMapper`: drop `status` (Phase 9 adds a run-status view).

- [ ] **3b.6 — Migration baseline** (`src/main/resources/db/migration/`)
  - Delete `V1`–`V7`; add `V1__baseline.sql` with `users`, `knowledge_bases` and `documents` only.
  - `documents`: no `status`; `UNIQUE (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION'`;
    plain index on `(knowledge_base_id, lesson_id)`; no global `content_hash` index.
  - Record the one-time squash in `docs/standards/backend/migrations.md` (done in v3.0).

- [ ] **3b.7 — Configuration and build**
  - `application.yml`: delete `spring.neo4j.*` and `spring.ai.vectorstore.*`. `application-dev.yml`: delete `spring.neo4j.*`.
  - `pom.xml`: delete `spring-boot-starter-data-neo4j`, `spring-ai-starter-vector-store-pgvector`, `testcontainers-neo4j`.
  - `env.example`: delete the `NEO4J_*` block.

- [ ] **3b.8 — Tests**
  - Delete `AgentResultTest`, `StepFingerprintTest`, `FlashcardDataTest`, `ArtifactRepositoryAdapterTest`,
    `integration/graph/`.
  - `DocumentRepositoryAdapterTest`: drop status; **add** "the same hash in two knowledge bases is not
    a duplicate across them".
  - `LessonIdentityTest`: Polish transliteration (`Mitoza komórkowa` → `mitoza-komorkowa`), `log` reserved.
  - `StubAIGateway`, `StubAIGatewayTest`, `AIGatewayAdapterTest`: drop `embed` and the embedding tests.
  - `TestContainerBase`: drop the Neo4j container; `pgvector/pgvector:pg15` → `postgres:15`.
  - `TestFixtures`: drop `makeDocumentArtifact` and the `status` parameter.

### Completion Checklist

- [ ] `mvn verify` passes.
- [ ] No reference remains to `DocumentArtifact`, `Agent`, `StepFingerprint`, `GraphIndexer`, Neo4j,
  pgvector or `embed` in `src/`.
- [ ] Cross-knowledge-base dedup test passes.
- [ ] Local databases dropped and recreated (the squash invalidates Flyway checksums).

---

## [ ] Phase 4 — Document Parsing and Ingestion

> **Changed in v3.0:** 4.5 and 4.6 — dedup by constraint per knowledge base, one `Document` per
> uploaded lesson version, no status, no checkpoints (ADR 0015).

**Goal:** Implement the `ParserRegistry`, all four document format parsers, `UploadSanitizer`,
heading-aware chunking, and `IngestionService` with per-knowledge-base deduplication.

### Tasks

- [ ] **4.1 — `UploadSanitizer`** (`dev.mindforge.infrastructure.security`)
  - Validates MIME type against allowlist (`text/markdown`, `application/pdf`,
    `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`).
  - Rejects oversized uploads (configurable max, default 50 MB).
  - Rejects path traversal attempts in filename.
  - Sanitizes filename to safe characters.

- [ ] **4.2 — `ParserRegistry`** (`dev.mindforge.infrastructure.parsing`)
  - MIME-dispatch map from content type → `DocumentParser` implementation.
  - All parsers registered via `@Configuration` (not discovered via classpath scan).

- [ ] **4.3 — Format parsers** (`dev.mindforge.infrastructure.parsing`)
  - `MarkdownParser`: extracts heading tree, code blocks, front-matter metadata.
  - `PdfParser`: wraps Apache PDFBox; extracts text per-page, document metadata.
  - `DocxParser`: wraps Apache POI; extracts paragraphs and heading styles, **in document order**
    (tables stay where the prose places them).
  - `PlainTextParser`: line-based extraction, best-effort heading detection.

- [ ] **4.4 — Heading-aware chunker** (`dev.mindforge.infrastructure.parsing`)
  - Splits `ContentBlock` list into overlapping chunks respecting heading boundaries.
  - Configurable chunk size (tokens) and overlap (tokens) via `ProcessingSettings`.
  - Produces deterministic chunks — same input always produces same chunks. Feeds Extract on long documents.

- [ ] **4.5 — `IngestionService`** (`dev.mindforge.application.service`)
  - Validates upload via `UploadSanitizer`; parses via `ParserRegistry`; resolves `LessonIdentity`.
  - Computes `ContentHash`. An identical upload into the same knowledge base returns the existing
    document id — enforced by `UNIQUE (knowledge_base_id, content_hash)`, not by check-then-insert.
  - A revised upload of the same lesson (different hash) is a **new** `Document`; there is no
    retraction of the old version's contributions.
  - Persists the `Document` and publishes `DocumentIngested` in the same `@Transactional` boundary.
  - Returns HTTP 202 semantics: the ingest run starts after commit (Phase 6).

- [ ] **4.6 — Unit tests**
  - `IngestionServiceTest`: identical upload returns existing id; same bytes into another knowledge
    base create a new document; a revised lesson creates a second `Document`; save + event publish together.
  - Parser unit tests: each parser extracts expected text and metadata from fixture files.
  - `UploadSanitizerTest`: MIME rejection, oversized rejection, path traversal rejection.

### Completion Checklist

- [ ] All four parsers extract text and metadata correctly from fixture files.
- [ ] Chunker produces deterministic, heading-aware chunks.
- [ ] Dedup is per knowledge base and enforced by the database constraint.
- [ ] `UploadSanitizer` rejects disallowed MIME types, oversized files, and path traversal.
- [ ] `DocumentIngested` event is published in the same transaction as the document save.

---

## [ ] Phase 5 — Wiki Domain and Store

> **Re-cut in v3.0.** This phase was *Agent Framework and Pipeline Orchestration* (`AgentRegistry`,
> `OrchestrationGraph`, step-fingerprint checkpointing). All of that is deleted (ADRs 0013, 0015).
> The phase now builds the wiki model, its storage, run records and revert.

**Goal:** Implement pages, paths, links, provenance, revisions, supersessions and ingest runs in the
domain; their PostgreSQL schema and `WikiStore` adapter; the ingest lease; the index and log renderers;
and restore-forward, tip-only revert.

### Tasks

- [ ] **5.1 — Wiki domain types** (`dev.mindforge.domain.model`) — ADRs 0010, 0011
  - `WikiPage(pageId, knowledgeBaseId, path, title, description, type, markdownBody, revision, createdAt, updatedAt)`.
  - `PageType` value object (non-empty, normalised) with constants `CONCEPT`, `SOURCE_SUMMARY`.
  - `PagePath`: `concepts/<slug>` or `sources/<lesson-id>`; `slugify` shared with `LessonIdentity`;
    final segment `index` / `log` rejected; directory fixed by type.
  - `PageLink(pageId, targetPath, fragment)`, `PageSource(pageId, documentId, ingestRunId)`,
    `PageRevision(pageId, revision, ingestRunId, title, description, type, markdownBody /* null = tombstone */, createdAt)`,
    `PageSupersession(supersessionId, supersededPageId, sectionAnchor, supersedingPageId, ingestRunId, createdAt)`.
  - `IngestRun(runId, knowledgeBaseId, kind INGEST|REVERT|LINT, documentId, revertsRunId, status
    RUNNING|WRITTEN|COMPLETED|FAILED, failureReason, failures, supersessionSkipped, stepVersions, findings, startedAt, finishedAt)`.
  - `KnowledgeBase`: add a derived `pageCount`.
  - `LinkParser` (pure): canonical `[Title](/dir/slug.md#fragment)` → `PageLink`s; heading anchor = `slugify(heading)`.

- [ ] **5.2 — Migration `V2__create_wiki_and_runs.sql`** — ADRs 0014, 0015
  - `wiki_pages` (`UNIQUE (knowledge_base_id, path)`, `UNIQUE (knowledge_base_id, page_id)`).
  - `page_links` with FK `(knowledge_base_id, source_page_id)` → `wiki_pages` `ON DELETE CASCADE`;
    index `(knowledge_base_id, target_path)`.
  - `page_revisions`, `page_sources`, `page_supersessions` — FK to `knowledge_bases` only, **no FK to `wiki_pages`**.
  - `ingest_runs`; `knowledge_bases.active_run_id`; `page_sources.document_id` FK `ON DELETE RESTRICT`.

- [ ] **5.3 — Ports** (`dev.mindforge.domain.port`)
  - `WikiStore` — page-shaped, `kbId` first on every method: `findByPath`, `findById`, `listPages`,
    `savePage` (writes page + revision + re-derived links), `deletePage` (tombstone revision + row delete),
    `reinsertPage`, `addSources`, `addSupersessions`, `deleteSourcesByRun`, `deleteSupersessionsByRun`,
    `listRevisions`, `danglingLinks`.
  - `IngestRunRepository`: `create`, `claimLease` (conditional update; returns whether claimed),
    `markWritten`, `complete`, `fail`, `findByStatus`, `oldestPendingDocument`.

- [ ] **5.4 — Persistence adapters** (`dev.mindforge.infrastructure.persistence`)
  - Entities, JPA repositories, MapStruct mappers and adapters for all of 5.2, per the sub-package convention.
  - Every query binds `knowledge_base_id`.

- [ ] **5.5 — Renderers** (`dev.mindforge.application.wiki`)
  - `IndexRenderer`: root index — `okf_version` frontmatter, `# Concepts` / `# Sources`,
    `* [title](/path.md) - description`, sorted by title. Used by model prompts, the SPA and export.
  - `LogRenderer`: date-grouped (UTC, newest first) lines for completed runs — **Ingest**, **Edit**,
    **Lint**, **Revert** — with counts derived from `page_revisions`.

- [ ] **5.6 — `RevertService`** (`dev.mindforge.application.service`) — ADR 0012
  - Creates a `REVERT` run under the lease.
  - For each page the target run revised: offered only if its highest revision carries the target run
    (tip check); a tombstone additionally requires the path to be free.
  - Restore-forward: append revision `r + 1` carrying `r − 1`'s content; a created page gets a tombstone;
    a deleted page is re-inserted under its `page_id`.
  - Delete the target run's `page_sources` and `page_supersessions`; single-supersession removal by id.

- [ ] **5.7 — Tests**
  - Unit: `PagePath`/`slugify` (Polish, reserved names); `PageType` normalisation; `LinkParser`
    (fragments, anchored links, wrong-directory links); `IndexRenderer`/`LogRenderer` output; tip check.
  - Integration (`@Testcontainers`): path uniqueness; a link row cannot reference another knowledge base's
    page; delete → tombstone → revert re-inserts with sources intact; two concurrent `claimLease` calls — exactly one wins.

### Completion Checklist

- [ ] No `deleted_at` filter exists anywhere — a deleted page is absent because its row is.
- [ ] Every `WikiStore` method takes `kbId` first.
- [ ] Revert is restore-forward: no revision row is ever deleted and `revision` is monotonic.
- [ ] The lease admits one page-writing run per knowledge base under concurrency.

---

## [ ] Phase 6 — Ingest Pipeline

> **Re-cut in v3.0.** This phase was *Core Processing Agents* — seven agents behind an `Agent`
> interface. Now: a fixed pipeline of concrete model services (ADR 0013). `SummarizerAgent` and
> `ConceptMapperAgent` are gone; flashcard and quiz generation move to Phase 10.

**Goal:** Ingest one document (or conversation turn) into its knowledge base's wiki end to end —
relevance guard, claim extraction against the index, resolve, parallel page writes, link check,
commit, supersession — with the lease, partial-success semantics and the startup sweep.

### Ingest contract

- **Input**: a `Document` with parsed `ContentBlock`s, or a `CONVERSATION` document whose instruction
  enters at Resolve (it *is* the claim set).
- **Output**: one ingest run, `COMPLETED` or `FAILED`, plus the pages, revisions, sources, links and
  supersessions it committed.
- **Zero successful page writes fails the run**; partial success lands and records `failures`.
- **Over the claim cap fails the run** — never truncates.

### Tasks

- [ ] **6.1 — Prompt files** (`src/main/resources/prompts/pl/`)
  - `relevance_guard.pl.md`, `claim_extractor.pl.md`, `page_writer.pl.md`, `link_checker.pl.md`,
    `supersession_detector.pl.md`.
  - Decide and write down here: section conventions per page type, and the prose language when a source's
    language differs from the prompt locale (handed off from the re-cut map's fog).
  - Every rule a prompt teaches is also enforced in code (see `ai_agents.md`).

- [ ] **6.2 — `Preprocessor` and `RelevanceGuard`** (`dev.mindforge.agent`)
  - `Preprocessor`: deterministic, no LLM — whitespace, headings, cleaned blocks.
  - `RelevanceGuard` (SMALL): returns `ValidationResult`; a rejection fails the run with its reason.

- [ ] **6.3 — `ClaimExtractor`** (LARGE)
  - Input: blocks + rendered index. Output: `ClaimSet` — each claim with a proposed existing target path or `new: <title>`.
  - Enforces the claim cap from `ProcessingSettings`.

- [ ] **6.4 — Resolve** (code, in `IngestPipeline`)
  - Verify proposed paths exist (a hallucinated path becomes a create); derive paths for new titles;
    collapse claims targeting the same new path; an existing exact path is a revision.
  - Add the document's `Source Summary` task (`sources/<lesson-id>`), unless the document is a conversation turn.

- [ ] **6.5 — `PageWriter`** (LARGE)
  - Input: task, existing body, linkable index (including paths this run will create).
  - Output: `PageDraft(title, description, body)`. Rejected if the body carries frontmatter, a `# Citations`
    section or an H1 repeating the title.

- [ ] **6.6 — `LinkChecker`** (SMALL) **and `LinkInsertionApplier`** (code) — ADR 0017
  - The model returns `List<LinkInsertion>`; code wraps the first eligible occurrence (outside links, code
    spans, headings) only if the target is live or created by this run; asserts strip-links equality.
  - Runs on in-memory bodies before commit; a failure records `{"step":"linkCheck"}` and commits without extra links.

- [ ] **6.7 — `SupersessionDetector`** (LARGE ×1)
  - Input: claim set + sections retrieval flagged as related but not revised. Output: supersession rows.

- [ ] **6.8 — `IngestPipeline` and ingest worker** (`dev.mindforge.application.service`)
  - `SpringEventPublisher implements EventPublisher`; `@TransactionalEventListener(AFTER_COMMIT)` on
    `DocumentIngested` wakes the worker.
  - Worker claims the lease; on a lost claim the document stays pending.
  - Writes fan out on virtual threads behind a semaphore sized for provider rate limits.
  - **Commit 1** (`@Transactional`): pages, revisions, sources, links, run `WRITTEN`.
  - **Commit 2**: supersessions, run `COMPLETED`, lease released; the worker claims the oldest pending document.
  - Failure: run `FAILED` with `failures` / `failure_reason`, lease released.
  - **Startup sweep**: `RUNNING` → `FAILED`; `WRITTEN` → `COMPLETED` + `supersession_skipped`; release
    leases; drain pending documents. Ceiling: assumes one instance.
  - Record each service's `VERSION` + model id in `step_versions`, and summed cost in `cost`.
  - `UploadSource` gains `CONVERSATION`; the conversation entry point is exposed by Phase 11.

- [ ] **6.9 — Tests** (`StubAIGateway` fixtures, no real HTTP)
  - Zero successful writes fails the run; partial success lands and records failures.
  - Claim cap exceeded fails loudly.
  - A hallucinated target path becomes a create.
  - A link insertion that would change prose is rejected.
  - Two uploads into one knowledge base serialize; into two knowledge bases they run concurrently.
  - The sweep settles `RUNNING` and `WRITTEN` runs.
  - Revert after an ingest restores every page it still tips.
  - `VERSION` constant present on each model service.

### Completion Checklist

- [ ] A real document ingests into pages end to end against `StubAIGateway` fixtures.
- [ ] No LLM call happens inside a database transaction.
- [ ] Pages written are counted from inserted rows, never from model output.
- [ ] Every model service declares `static final String VERSION`, recorded on the run.

---

## [ ] Phase 7 — Lint

> **Number reused in v3.0.** Phase 7 was the *Neo4j Graph Layer*, deleted by ADR 0016 (the graph is
> `page_links`). Lint occupies the same position in the dependency graph, right after ingest.

**Goal:** Implement live structural health checks and the on-demand full review (ADR 0017). The link
check inside ingest is already built in Phase 6.

### Tasks

- [ ] **7.1 — Health queries** (`WikiStore` / persistence adapter)
  - Dangling links; wrong-directory links (a dangling link whose final segment matches a live page elsewhere);
    orphan Concepts (no inbound links); duplicate titles; dangling supersessions (anchor gone or superseding page gone).
  - `KnowledgeBaseHealth` record. No run, nothing stored.

- [ ] **7.2 — `WikiReviewer`** (LARGE) **and `LintService`**
  - A `LINT` run under the lease; reads live Concept bodies in chunks with the (prefiltered) index.
  - Link insertions through `LinkChecker` + `LinkInsertionApplier`; findings (contradictions, unmarked
    supersessions) and suggestions (missing pages, questions to investigate) stored in `ingest_runs.findings`.
  - Never writes prose; never inserts supersessions; never generates a page from a suggestion.

- [ ] **7.3 — Tests**
  - Each health query against fixture data.
  - `LintService` with `StubAIGateway`: insertions applied, prose unchanged, findings stored, lease respected.

### Completion Checklist

- [ ] Health checks run without an LLM call or a run.
- [ ] A full review's insertions are revertible like any run.
- [ ] Lint has no code path that writes a page body other than link insertion.

---

## [ ] Phase 8 — Event System

> **Changed in v3.0.** Keeps its design decision — no outbox, no Redis — and drops the Neo4j indexing
> listener. `SpringEventPublisher` moved into Phase 6, where the ingest worker first needs it.

**Goal:** Publish run-level domain events and stream real-time ingest progress to the browser.

> **Design decision** (unchanged, now ADR 0015): no transactional outbox. Every consumer is derived or
> best-effort; listeners run after commit and tolerate a missed event.

### Tasks

- [ ] **8.1 — Run events** (`dev.mindforge.domain.model.DomainEvent`)
  - Add `IngestStepCompleted(runId, step)`, `IngestRunCompleted(runId, knowledgeBaseId)`,
    `IngestRunFailed(runId, reason, retryable)`, published inside the corresponding commits.

- [ ] **8.2 — `SseProgressEmitter`** (`dev.mindforge.infrastructure.event`)
  - In-memory registry: `Map<UUID, SseEmitter>` keyed by run id.
  - `@TransactionalEventListener(AFTER_COMMIT)` on `IngestStepCompleted`: sends step name + status as JSON.
  - Emitter cleaned up on `IngestRunCompleted` or `IngestRunFailed`.

- [ ] **8.3 — Unit tests**
  - `SseProgressEmitterTest`: emitter receives step events; cleaned up on completion/failure; a missing
    emitter is a no-op.

### Completion Checklist

- [ ] Domain events published via `EventPublisher` reach their listeners after commit.
- [ ] SSE emitter sends step-level progress without Redis or an external broker.

---

## [ ] Phase 9 — API Layer (Spring MVC)

> **Changed in v3.0:** 9.6 and 9.7 — controllers for the wiki, runs, revert and health replace
> `ArtifactController`; documents cannot be deleted individually (ADR 0015).

**Goal:** Implement the auth system (Google/GitHub OAuth2 + email/password + JWT), all REST controllers,
security config, global exception handler, and SPA serving.

### Tasks

- [ ] **9.1 — `MindForgeApplication.java`** (`dev.mindforge`) — `@SpringBootApplication` entry point. No business logic.

- [ ] **9.2 — `SecurityConfig.java`** (`dev.mindforge.api.config`)
  - Spring Security filter chain: CSRF disabled for API paths, JWT cookie filter,
    OAuth2 login (Google, GitHub), stateless session for API endpoints.
  - JWT stored in `HttpOnly; Secure; SameSite=Lax` cookie — never in response body.
  - `BCryptPasswordEncoder` with cost 12.

- [ ] **9.3 — `JwtFilter.java`** — reads JWT from cookie, validates, sets `SecurityContext`.

- [ ] **9.4 — `AuthController.java`** (`dev.mindforge.api.controller`)
  - `POST /api/auth/register`, `POST /api/auth/login` (sets JWT cookie), `POST /api/auth/logout`,
    `GET /api/auth/me` (never includes `passwordHash`). OAuth2 callbacks handled by Spring Security.

- [ ] **9.5 — Request/response DTOs** (`dev.mindforge.api.dto`) — all Java `record` types.
  - Never expose: `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion`, `cost`,
    `step_versions`, raw `failures` internals.

- [ ] **9.6 — `GlobalExceptionHandler.java`** (`@ControllerAdvice`)
  - `LessonIdentityException` → 422; `PageNotFoundException` / `DocumentNotFoundException` → 404;
    `RevertNotAllowedException` → 409; `AccessDeniedException` → 403; `DeadlineExceededException` → 503.
  - Uniform `{ "error": "...", "code": "...", "detail": "..." }` shape. Ingest failures are run
    state, not HTTP errors.

- [ ] **9.7 — REST controllers** (`dev.mindforge.api.controller`)
  - `DocumentController`: upload (202 + document id), list, get — with latest-run status.
  - `KnowledgeBaseController`: CRUD for knowledge bases (delete cascades everything).
  - `WikiController`: rendered index; page by path (body with supersession notes and citations);
    page revisions and diffs; the graph (live pages + `page_links`).
  - `RunController`: list runs; run report (pages written with diffs, claims superseded); revert a run;
    remove one supersession; SSE progress `GET /api/runs/{id}/progress`.
  - `HealthController`: `GET /api/knowledge-bases/{kbId}/health`; start a full review.
  - `UserController`: profile management.
  - Every method thin: validation + ownership check + delegate. Constructor injection only.

- [ ] **9.8 — SPA serving**
  - Static Angular build served from classpath under `/static/`; non-API paths fall through to `index.html`.

- [ ] **9.9 — API integration tests** (`integration/api/`)
  - Auth flow: register → login → access protected resource → logout.
  - Upload flow: upload → run completes (stub gateway) → pages listed → no sensitive fields in any response.
  - Ownership: user A cannot read user B's pages, runs or health (403).

### Completion Checklist

- [ ] All controllers thin — no business logic; all delegated to application services.
- [ ] JWT stored in `HttpOnly` cookie only — never in response body.
- [ ] No sensitive fields in any API response (verified by integration tests).
- [ ] Ownership check present on every `@RestController` method.

---

## [ ] Phase 9b — Bundle Export

> **New in v3.0.** Decided in [T11](../wayfinder/tickets/11-bundle-export.md).

**Goal:** Download a knowledge base as a conformant OKF bundle.

### Tasks

- [ ] **9b.1 — `BundleExporter`** (`dev.mindforge.infrastructure.export`)
  - One `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` snapshot; no lease.
  - Per page: SnakeYAML frontmatter (`type`, `title`, `description`, `timestamp`) + stored body verbatim +
    supersession notes as a blockquote under the superseded heading + projected `# Citations`.
  - `index.md` from `IndexRenderer`; `log.md` from `LogRenderer`.
  - Reads only wiki tables, `ingest_runs` counts and `documents` title/filename/date — never study tables,
    `cost`, `step_versions`, `failures` or `findings`.

- [ ] **9b.2 — `BundleConformanceValidator`** (pure function over rendered files)
  - OKF §9: parseable frontmatter; non-empty `type`; `index.md` / `log.md` structure. Plus path pattern
    and reserved segments. A violation is a 500 and is logged — never a shipped bundle.

- [ ] **9b.3 — Endpoint**
  - `GET /api/knowledge-bases/{kbId}/export` → `application/zip`, `Content-Disposition: attachment;
    filename="<kb-name-slug>-okf.zip"`, streamed via `StreamingResponseBody`; ownership checked.

- [ ] **9b.4 — Tests**
  - Validator as oracle over exporter output; YAML escaping of titles with `: ` and quotes; a concurrent
    ingest commit does not tear the bundle; no study data in any file.

### Completion Checklist

- [ ] Every exported bundle passes the conformance validator.
- [ ] No new dependency added.

---

## [ ] Phase 10 — Quiz and Flashcard Services

> **Changed in v3.0.** Study material is cut from Concept pages (ADR 0018). `FlashcardGenerator`,
> `QuizGenerator` and `QuizEvaluator` arrive here as model services, not Phase 6 agents. Graph RAG
> targeting is replaced by weak-page SQL.

**Goal:** Implement flashcards with SM-2 that survive page rewrites, and server-authoritative quizzes
targeting weak pages.

### Tasks

- [ ] **10.1 — Domain types** (`dev.mindforge.domain.model`)
  - `ReviewResult` record: `int rating` (0–5).
  - `Flashcard(cardId, pageId, sectionAnchor, cardType, front, back, generatedAtRevision)`;
    `cardId = sha256(kbId|pageId|cardType|front|back)[:16]`.
  - `StudyScope` sealed interface: `WholeKnowledgeBase`, `Lesson(lessonId)`, `Page(pageId)`.
  - Ports: `StudyProgressStore` (due cards, save review, weak pages), `QuizSessionStore`.
  - `ProcessingSettings.newPagesPerSession` (default 10).

- [ ] **10.2 — Migration `V3__create_study.sql` and stores**
  - `flashcards` (PK `(knowledge_base_id, card_id)`, `page_id` without FK to `wiki_pages`, `retired_at`, SM-2 columns).
  - `study_events (knowledge_base_id, page_id, card_id, kind CARD|QUIZ, score, occurred_at)`.
  - `quiz_sessions` — questions JSONB with reference answers and grounding; TTL via `@Scheduled` cleanup;
    Caffeine wraps the PostgreSQL store.

- [ ] **10.3 — SM-2 algorithm** (`dev.mindforge.application.service`)
  - `SM2Scheduler` pure Java class. `EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))`, EF ≥ 1.3.

- [ ] **10.4 — `FlashcardGenerator`** (LARGE) **and `FlashcardService`**
  - Lazy: when a deck opens for a scope, generate cards for at most `newPagesPerSession` Concept pages
    without cards; regenerate a page's cards when `page.revision > generated_at_revision`, passing current
    cards for verbatim reuse.
  - Unchanged content keeps its id and history; vanished cards get `retired_at`; reappearing ids are un-retired.
  - Due query inner-joins `wiki_pages` and excludes cards whose `(page_id, section_anchor)` has a live supersession.
  - `reviewCard` updates SM-2 and appends a `study_events` row.

- [ ] **10.5 — `QuizGenerator`** (LARGE), **`QuizEvaluator`** (SMALL) **and `QuizService`**
  - `startSession(kbId, userId, scope)`: weakest pages first (mean of recent `study_events`), then 1-hop
    `page_links` neighbours; one generation call with superseded sections stripped; batch stored in the session.
  - `nextQuestion`: question text only. `submitAnswer`: grade against the session's reference answer and
    grounding excerpt; return score + feedback; append `study_events`.

- [ ] **10.6 — Controllers**
  - `QuizController`: `POST /api/quiz/sessions`, `GET /api/quiz/sessions/{id}/next`, `POST /api/quiz/sessions/{id}/answers`.
  - `FlashcardController`: `GET /api/flashcards?kbId&scope`, `POST /api/flashcards/{id}/reviews`.

- [ ] **10.7 — Tests**
  - `SM2SchedulerTest`: rating 5 → EF increases; rating 0 → reset; EF never below 1.3.
  - `FlashcardServiceTest`: a revision keeps unchanged cards' history; a changed answer retires the old card;
    revert revives it with history; superseded sections are skipped; the new-page limit is honoured.
  - `QuizServiceTest` with `StubAIGateway`: no reference answer in responses; weak pages targeted first.

### Completion Checklist

- [ ] SM-2 produces correct scheduling for all ratings (0–5).
- [ ] Quiz responses contain no `referenceAnswer` or `groundingContext`.
- [ ] Flashcards are never written into a page and never exported.
- [ ] Due cards are always scoped to `kbId`.

---

## [ ] Phase 11 — Query

> **Re-cut in v3.0.** This phase was *Search and Conversational RAG* over `content_embeddings`. Query
> reads curated pages chosen from the rendered index instead (ADR 0016). pgvector is gone.

**Goal:** Answer multi-turn questions from the wiki with page citations; accept conversation edits to the
wiki; provide page search for the SPA.

### Tasks

- [ ] **11.1 — Domain types**
  - `Interaction(interactionId, userId, kbId, startedAt)`;
    `InteractionTurn(turnId, question, answer, usedPagePaths, createdAt)`;
    `TokenBudget(totalTokens, reservedForResponse)` with `availableForContext()`.
  - `InteractionStore` port: `createInteraction`, `addTurn`, `getInteraction`, `listForUser` (redacted), `listUnredacted`.

- [ ] **11.2 — `PageSelector`** (SMALL), **`AnswerWriter`** (LARGE) **and `QueryService`**
  - Select: rendered index (prefiltered past 20K tokens) + question + prior turns → page paths.
  - Load: verify live paths; bodies with supersession notes; add 1-hop link neighbours while `TokenBudget` allows.
  - Answer: `DeadlineProfile.INTERACTIVE`; cites page paths. Query never writes pages and takes no lease.

- [ ] **11.3 — Conversation edits** — ADR 0012
  - An explicit *edit* action in chat (not a question): the instruction is persisted as a `CONVERSATION`
    `Document` and ingested as a run entering at Resolve (Phase 6). "Save that" carries the previous answer
    as its content; deletion requests go through the same channel.
  - The user sees the run report inline; "undo" calls `RevertService`.

- [ ] **11.4 — Page search** (`SearchService`)
  - Title/description match and `simple` full-text over `wiki_pages` bodies, scoped to `kbId`.
  - Shares the lexical code the index prefilter will use; `pg_trgm` is added only when a knowledge base passes the ceiling.

- [ ] **11.5 — `InteractionStoreAdapter`** (PostgreSQL)
  - `listForUser()` returns only `question` + `answer`; `listUnredacted()` is admin-only.

- [ ] **11.6 — Controllers**
  - `QueryController`: `POST /api/query/sessions`, `POST /api/query/sessions/{id}/messages`,
    `POST /api/query/sessions/{id}/edits`.
  - `SearchController`: `GET /api/knowledge-bases/{kbId}/pages/search?q=...`.

- [ ] **11.7 — Tests**
  - `QueryServiceTest` with `StubAIGateway`: hallucinated paths dropped; `TokenBudget` trimming; no grounding in responses.
  - Conversation edit creates a `CONVERSATION` document and a run; an identical edit a week later is not deduplicated.
  - `InteractionStoreAdapterTest`: `listForUser` redacts internals.

### Completion Checklist

- [ ] Query answers cite pages, never uploads directly.
- [ ] Query has no write path; conversation edits are ingest runs.
- [ ] Responses never include `groundingContext`, `rawPrompt` or `rawCompletion`.

---

## [ ] Phase 12 — Angular Frontend

> **Changed in v3.0:** the document-centric summary/flashcards/concept-map tabs are replaced by wiki,
> run, health, study and chat surfaces.

**Goal:** Create the Angular SPA with standalone components, lazy-loaded routing, auth integration and
all user-facing pages for the learning loop.

### Tasks

- [ ] **12.1 — Angular project setup**
  - `ng new frontend --standalone --routing --style=scss` inside `frontend/`.
  - Install: `@angular/material`, `cytoscape`, `@types/cytoscape`. Proxy `/api` to `:8080`.

- [ ] **12.2 — Core infrastructure**
  - `AuthService` (JWT cookie, Google/GitHub OAuth2), `ApiService` (generated types), `AuthGuard`,
    global `HttpInterceptor` mapping 4xx/5xx to toasts.

- [ ] **12.3 — Pages**
  - `/login`; `/dashboard` — knowledge bases.
  - `/kb/:id` — documents, upload dropzone, recent runs with live SSE progress.
  - `/kb/:id/pages` — the index, grouped Concepts / Sources, with page search.
  - `/kb/:id/pages/:path` — a page with supersession notes, citations and revision history.
  - `/kb/:id/graph` — Cytoscape.js graph of pages and `page_links`.
  - `/kb/:id/runs/:runId` — run report: pages written with diffs, claims superseded with per-row removal, revert.
  - `/kb/:id/health` — live findings, latest full review's findings and suggestions, "run full review".
  - `/kb/:id/study` — scope picker, flashcard review, quiz.
  - `/kb/:id/chat` — Query conversation with an explicit edit action and inline run reports.
  - Export button on `/kb/:id`.

- [ ] **12.4 — Real-time ingest progress**
  - `EventSourceService` subscribes to `GET /api/runs/{id}/progress`; progress stepper per `IngestStepCompleted`.

- [ ] **12.5 — Build integration**
  - `npm run build` output lands in `frontend/dist/frontend/browser/`; `frontend-maven-plugin` copies it into
    `src/main/resources/static/` during `mvn package`.

### Completion Checklist

- [ ] `npm start` serves the SPA on `:4200` with proxy to `:8080`; `mvn package` embeds the build.
- [ ] All routes navigable; lazy loading confirmed.
- [ ] Revert is reachable from every run report while it is offered.
- [ ] API models match backend DTOs (generated).

---

## [ ] Phase 13 — Docker and Deployment

> **Changed in v3.0:** no Neo4j service; plain PostgreSQL.

**Goal:** Complete Docker multi-stage build, Docker Compose for local and production, and Railway/Render
deployment configuration.

### Tasks

- [ ] **13.1 — Multi-stage `Dockerfile`**
  - Stage 1 (`node:20-alpine`): `npm ci && npm run build` in `frontend/`.
  - Stage 2 (`maven:3.9-eclipse-temurin-21`): `mvn package -DskipTests`.
  - Stage 3 (`eclipse-temurin:21-jre-alpine`): `COPY --from=2 target/*.jar app.jar`; `EXPOSE 8080`.

- [ ] **13.2 — `compose.yml`**
  - Services: `app`, `postgres` (PostgreSQL 15). Health checks on both; `app` depends on `postgres` health.
  - Volume mount for PostgreSQL data. `compose.override.yml` for local dev.

- [ ] **13.3 — Deployment configuration**
  - `railway.json` (or `render.yaml`); `Procfile` fallback; `docs/project/deployment.md`.
  - Single instance (the ingest startup sweep assumes it).

- [ ] **13.4 — Smoke test**
  - `docker build -t mindforge .`; `docker compose up`; `curl /health` → 200; `curl /` → SPA index.

### Completion Checklist

- [ ] `docker build` creates a working multi-stage image.
- [ ] `docker compose up` starts both services with passing health checks.
- [ ] Application is deployable to Railway/Render via documented procedure.

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
    same knowledge base, and queues behind the lease like any upload.
  - Disabled unless `ProcessingSettings.fetchExternalArticles` is true.

- [ ] **17.3 — Unit tests**
  - `EgressPolicyTest`; `ArticleFetcherTest` with a mock HTTP server, including a policy violation.

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
                                  Phase 4  (parsing, upload, dedup)
                                       │
                                  Phase 5  (wiki domain & store, runs, lease, revert)
                                       │
                                  Phase 6  (ingest pipeline)
                                       │
                                  Phase 7  (Lint) ──► Phase 8 (run events, SSE)
                                                           │
                                                      Phase 9 (API) ──► Phase 9b (export)
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
