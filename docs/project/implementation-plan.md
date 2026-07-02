# MindForge — Implementation Plan

> **Version:** 2.0
> **Date:** 2026-06-15
> **Status:** Active
> **Reference:** [architecture.md](./architecture.md)

---

## Table of Contents

**Core System (Phases 0–13)**

1. [Phase 0 — Project Scaffolding and Tooling](#phase-0--project-scaffolding-and-tooling)
2. [Phase 1 — Domain Layer](#phase-1--domain-layer)
3. [Phase 2 — Infrastructure Foundation](#phase-2--infrastructure-foundation)
4. [Phase 3 — AI Gateway](#phase-3--ai-gateway)
5. [Phase 4 — Document Parsing and Ingestion](#phase-4--document-parsing-and-ingestion)
6. [Phase 5 — Agent Framework and Pipeline Orchestration](#phase-5--agent-framework-and-pipeline-orchestration)
7. [Phase 6 — Core Processing Agents](#phase-6--core-processing-agents)
8. [Phase 7 — Neo4j Graph Layer](#phase-7--neo4j-graph-layer)
9. [Phase 8 — Event System](#phase-8--event-system)
10. [Phase 9 — API Layer (Spring MVC)](#phase-9--api-layer-spring-mvc)
11. [Phase 10 — Quiz and Flashcard Services](#phase-10--quiz-and-flashcard-services)
12. [Phase 11 — Search and Conversational RAG](#phase-11--search-and-conversational-rag)
13. [Phase 12 — Angular Frontend](#phase-12--angular-frontend)
14. [Phase 13 — Docker and Deployment](#phase-13--docker-and-deployment)

**Post-MVP Enhancements (Phases 14–21)**

15. [Phase 14 — Observability and Tracing](#phase-14--observability-and-tracing)
16. [Phase 15 — CLI Entry Points](#phase-15--cli-entry-points)
17. [Phase 16 — Image Analysis Agent](#phase-16--image-analysis-agent)
18. [Phase 17 — Article Fetcher Agent](#phase-17--article-fetcher-agent)
19. [Phase 18 — Discord Bot](#phase-18--discord-bot)
20. [Phase 19 — Slack Bot](#phase-19--slack-bot)
21. [Phase 20 — Security Hardening](#phase-20--security-hardening)
22. [Phase 21 — End-to-End Testing and CI/CD](#phase-21--end-to-end-testing-and-cicd)

23. [Dependency Graph](#dependency-graph)

---

## Overview

This plan decomposes MindForge into 22 sequential phases. Phases 0–13 deliver the core
learning system: document ingestion, AI pipeline, knowledge graph, quiz/flashcard engine,
RAG chat, Angular SPA, and Docker deployment. Phases 14–21 layer in observability,
delivery channels, and quality gates after the core system is running.

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

- [x] **3.4 — `StubAIGateway`** (`src/test/java/.../support/`)
  - Implements `AIGateway`.
  - Builder API: `StubAIGateway.builder().willReturn(ModelTier.LARGE, "my response").build()`.
  - Captures all calls for assertion in tests.
  - Never makes real HTTP calls.

- [x] **3.5 — Unit tests**
  - `AIGatewayAdapterTest`: model-tier routing resolves correct model strings; response
    mapping into `CompletionResult`; deadline timeout throws `DeadlineExceededException`
    (exercised fast via injected millisecond-scale deadlines, not real 10s waits); embed
    delegation.
  - `StubAIGatewayTest`: canned response delivery, default fallback, call capture, no
    real HTTP calls.

### Completion Checklist

- [x] `AIGateway` is never instantiated directly — always resolved via Spring DI
  (`AiConfig` `@Bean`).
- [x] `ModelTier` routing is configuration-driven, not hardcoded strings.
- [x] Deadline profiles enforce correct timeouts (verified by timeout test with mock HTTP).
- [x] `StubAIGateway` is available for all downstream phases.

---

## [ ] Phase 4 — Document Parsing and Ingestion

**Goal:** Implement the `ParserRegistry`, all four document format parsers, `UploadSanitizer`,
heading-aware chunking, and `IngestionService` with deduplication and revision management.

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
  - `DocxParser`: wraps Apache POI; extracts paragraphs and heading styles.
  - `PlainTextParser`: line-based extraction, best-effort heading detection.

- [ ] **4.4 — Heading-aware chunker** (`dev.mindforge.infrastructure.parsing`)
  - Splits `ContentBlock` list into overlapping chunks respecting heading boundaries.
  - Configurable chunk size (tokens) and overlap (tokens) via `ProcessingSettings`.
  - Produces deterministic chunks — same input always produces same chunks.

- [ ] **4.5 — `IngestionService`** (`dev.mindforge.application.service`)
  - Validates upload via `UploadSanitizer`.
  - Computes `ContentHash`; checks `DocumentRepository.findByContentHash()` for dedup.
  - On dedup: returns existing document ID without reprocessing.
  - On new document: persists `Document` with `PENDING` status, publishes
    `DocumentIngested` event via `EventPublisher` — both in the same `@Transactional` boundary.
  - On re-upload of same lesson with new content (hash differs): creates a new revision,
    invalidates stale checkpoints for affected pipeline steps.

- [ ] **4.6 — Unit tests**
  - `IngestionServiceTest`: dedup returns existing ID; new document persists + publishes event;
    revision invalidates stale checkpoints.
  - Parser unit tests: each parser extracts expected text and metadata from fixture files.
  - `UploadSanitizerTest`: MIME rejection, oversized rejection, path traversal rejection.

### Completion Checklist

- [ ] All four parsers extract text and metadata correctly from fixture files.
- [ ] Chunker produces deterministic, heading-aware chunks.
- [ ] Ingestion deduplication and revision management work correctly.
- [ ] `UploadSanitizer` rejects disallowed MIME types, oversized files, and path traversal.
- [ ] `DocumentIngested` event is published in the same transaction as the document save.

---

## [ ] Phase 5 — Agent Framework and Pipeline Orchestration

**Goal:** Implement `AgentRegistry`, `PipelineOrchestrator` with DAG-aware checkpointing
and fingerprint invalidation, and the background worker that processes documents.

### Tasks

- [ ] **5.1 — `AgentRegistry`** (`dev.mindforge.agent`)
  - Holds a `Map<String, Agent>` of all registered agents.
  - Populated via `@Configuration` — each `Agent` bean is registered by name.
  - `get(String name)` throws if agent is not registered (fail fast).

- [ ] **5.2 — `OrchestrationGraph`** (`dev.mindforge.agent`)
  - Defines the DAG of agent execution order as a `List<List<String>>` (steps × parallel group).
  - Default pipeline: `[preprocessor]` → `[relevance_guard]` → `[summarizer, flashcard_generator,
    concept_mapper]` → `[quiz_generator]`.
  - Configurable via `@ConfigurationProperties` (agents can be enabled/disabled per environment).

- [ ] **5.3 — `PipelineOrchestrator`** (`dev.mindforge.application.service`)
  - Loaded with the `OrchestrationGraph` and `AgentRegistry`.
  - For each pipeline step:
    - Computes `StepFingerprint` from current inputs.
    - If fingerprint matches stored `StepCheckpoint` → skips the step (resume semantics).
    - If fingerprint differs or no checkpoint → executes the `Agent`.
    - On `AgentResult.Success` → saves `StepCheckpoint`; publishes `PipelineStepCompleted`.
    - On `AgentResult.Failure(retryable=true)` → reschedules (exponential back-off, max 3 retries).
    - On `AgentResult.Failure(retryable=false)` → marks document `FAILED`; publishes `ProcessingFailed`.
  - Fingerprint invalidation: when an upstream step's output changes, all downstream checkpoints
    are deleted before re-execution.

- [ ] **5.4 — Background worker** (`dev.mindforge.infrastructure`)
  - Polls `DocumentRepository` for documents with `PENDING` or `FAILED` status.
  - Executed on a virtual thread (`@Async` with a virtual-thread executor from Spring Boot 4.1).
  - Stale recovery: documents stuck in `PROCESSING` for > configurable threshold are reset to `PENDING`.

- [ ] **5.5 — Unit tests**
  - `PipelineOrchestratorTest`: checkpoint hit → agent skipped; checkpoint miss → agent called;
    upstream change → downstream checkpoints invalidated; retry logic on transient failure.
  - `OrchestrationGraphTest`: topological order is valid; disabled agents are excluded.

### Completion Checklist

- [ ] Orchestrator executes agent graph in correct topological order.
- [ ] Checkpoint skip works when fingerprint matches; invalidation cascades downstream.
- [ ] Retry with exponential back-off works for transient failures.
- [ ] Background worker claims tasks on virtual threads without blocking the API thread pool.

---

## [ ] Phase 6 — Core Processing Agents

**Goal:** Implement the seven core pipeline agents. Each agent implements the `Agent`
interface, declares a `VERSION` constant, and is registered in `AgentRegistry`.

> **Deferred to later phases**: `ImageAnalyzerAgent` (Phase 16), `ArticleFetcherAgent` (Phase 17).

### RelevanceGuardAgent contract (defined here before implementation)

- **Input**: preprocessed `ContentBlock` list + document metadata (from `AgentContext`).
- **Output**: writes `ValidationResult` to `DocumentArtifact.relevanceValidation`.
- **Model tier**: `SMALL` — binary classification task.
- **Pass behavior**: pipeline continues to next step.
- **Fail behavior**: sets `DocumentStatus` to `FAILED` with reason
  `ProcessingFailedReason.IRRELEVANT_CONTENT`; publishes `ProcessingFailed` event;
  stops the pipeline. The API surfaces the `reason` string to the user ("document rejected:
  does not appear to be a learning resource").
- **Purpose**: prevent accidentally uploaded files (invoices, photos, binary exports) from
  consuming expensive LARGE-model pipeline steps downstream.

### Tasks

- [ ] **6.1 — Prompt templates** (`src/main/resources/prompts/pl/`)
  - One `.{locale}.md` file per agent per locale: `preprocessor.pl.md`, `relevance_guard.pl.md`, `summarizer.pl.md`,
    `flashcard_generator.pl.md`, `concept_mapper.pl.md`, `quiz_generator.pl.md`, `quiz_evaluator.pl.md`.
  - Templates use `{placeholder}` syntax compatible with Spring AI `PromptTemplate`.

- [ ] **6.2 — `PreprocessorAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: none (deterministic, no LLM call).
  - Normalizes whitespace, detects and extracts headings, produces cleaned `ContentBlock` list.

- [ ] **6.3 — `RelevanceGuardAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: `SMALL`.
  - Calls `AIGateway.complete(SMALL, ...)` with relevance classification prompt.
  - Parses response to populate `ValidationResult`.
  - On `passed == false`: throws `IrrelevantContentException` (pipeline catches this and
    marks the document `FAILED`).

- [ ] **6.4 — `SummarizerAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: `LARGE`.
  - Generates `SummaryData` from `ContentBlock` list.

- [ ] **6.5 — `FlashcardGeneratorAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: `LARGE`.
  - Generates `List<FlashcardData>` with deterministic `cardId` computation.

- [ ] **6.6 — `ConceptMapperAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: `LARGE`.
  - Generates `ConceptMapData` with typed nodes and edges.

- [ ] **6.7 — `QuizGeneratorAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: `LARGE`.
  - Generates quiz questions targeting concept weaknesses (initially from concept map;
    full Graph RAG in Phase 10).

- [ ] **6.8 — `QuizEvaluatorAgent`** (`dev.mindforge.agent`)
  - `VERSION = "1.0"`. Model tier: `SMALL`.
  - Evaluates a user's free-text answer against a reference answer.
  - Returns score (0–5) and feedback string.

- [ ] **6.9 — Register all agents** in `@Configuration`

- [ ] **6.10 — Unit tests**
  - Each agent tested with `StubAIGateway` returning well-formed fixture responses.
  - `RelevanceGuardAgent`: `passed=true` continues; `passed=false` throws `IrrelevantContentException`.
  - `FlashcardGeneratorAgent`: same inputs produce same `cardId`.
  - `VERSION` constant present on each agent class.

### Completion Checklist

- [ ] All 7 agents implemented and registered.
- [ ] Each agent declares `static final String VERSION`.
- [ ] All agents tested with `StubAIGateway` — zero real HTTP calls in unit tests.
- [ ] `RelevanceGuardAgent` failure propagates correctly through orchestrator.

---

## [ ] Phase 7 — Neo4j Graph Layer

**Goal:** Implement the Neo4j graph adapter, indexer, retrieval port, and Cypher queries
for concept graph management, Graph RAG, and weak concept detection.

### Tasks

- [ ] **7.1 — `Neo4jIndexerAdapter implements GraphIndexer`**
  (`dev.mindforge.infrastructure.graph`)
  - `indexArtifact()`: writes/merges `Concept` nodes and `RELATES_TO` edges from `ConceptMapData`
    using `UNWIND` batches for efficiency.
  - `removeByLesson()`: deletes all nodes/edges scoped to the lesson on revision.
  - All writes scoped to `kbId` property on nodes.

- [ ] **7.2 — Cypher queries** (`dev.mindforge.infrastructure.graph`)
  - `findConceptNeighborhood(UUID kbId, String concept, int depth)`: returns `ConceptNode` list.
  - `findWeakConcepts(UUID kbId, UUID userId)`: returns concepts with below-threshold retention.
  - Queries are constants on a `CypherQueries` utility class — never inline strings.

- [ ] **7.3 — Add domain types for retrieval** (`dev.mindforge.domain.model`)
  - `ConceptNode` record: `String id`, `String label`, `float retentionScore`.
  - `ConceptNeighborhood` record: `ConceptNode center`, `List<ConceptNode> neighbors`, `List<ConceptEdge> edges`.
  - `RetrievalPort` interface in `dev.mindforge.domain.port`: `retrieveNeighborhood`,
    `findWeakConcepts`, `getLessonConcepts`.

- [ ] **7.4 — `Neo4jRetrievalAdapter implements RetrievalPort`**
  - Implements all retrieval queries, scoped to `kb_id`.
  - Follows retrieval priority: graph-first, then falls back to full-text, then to vector.

- [ ] **7.5 — `StubRetrievalAdapter`** (`src/test/java/.../support/`)
  - Returns configurable fixture data for unit tests without a Neo4j container.

- [ ] **7.6 — Neo4j indexes and constraints**
  - Created on first run via `schema.cypher` executed at startup via `@EventListener(ApplicationReadyEvent.class)`.
  - `CONSTRAINT concept_id UNIQUE ON (c:Concept) ASSERT c.id IS UNIQUE`.
  - Index on `(:Concept {kbId})` and `(:Concept {label})`.

- [ ] **7.7 — Integration tests**
  - `@Testcontainers` with real Neo4j 5.
  - Round-trip: index artifact → verify nodes/edges created → remove lesson → verify deleted.
  - Neighborhood retrieval returns correct depth-N results.

### Completion Checklist

- [ ] Graph indexer writes correct nodes/edges using `UNWIND` batches.
- [ ] Lesson revision cleanup removes stale nodes/edges scoped to that lesson.
- [ ] Retrieval follows graph-first → full-text → vector priority.
- [ ] All queries scoped to `kbId` — no cross-KB data leakage.

---

## [ ] Phase 8 — Event System

**Goal:** Implement lightweight event wiring: Spring `@TransactionalEventListener` for
Neo4j indexing after commit, and in-memory SSE emitter registry for real-time pipeline
progress updates to the browser.

> **Design decision**: A full transactional outbox with a relay process and Redis Pub/Sub
> is not justified here — Neo4j is the only consumer and it is optional/rebuildable.
> The full outbox pattern is documented in Future Considerations for when multiple
> independent consumers exist.

### Tasks

- [ ] **8.1 — `SpringEventPublisher implements EventPublisher`**
  (`dev.mindforge.infrastructure.event`)
  - Wraps `ApplicationEventPublisher`.
  - `publish(DomainEvent event)` calls `applicationEventPublisher.publishEvent(event)`.

- [ ] **8.2 — `GraphIndexingListener`**
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` on `ProcessingCompleted`.
  - Calls `GraphIndexer.indexArtifact(artifact)`.
  - On `Neo4jException`: logs warning and schedules a retry via `@Scheduled` (simple
    exponential back-off, max 3 attempts). Failure does not roll back the document.

- [ ] **8.3 — `SseProgressEmitter`** (`dev.mindforge.infrastructure.event`)
  - In-memory registry: `Map<UUID, SseEmitter> activeEmitters` (document ID → emitter).
  - `@EventListener` on `PipelineStepCompleted`: looks up emitter for `documentId`,
    sends step name + status as JSON.
  - Emitter is cleaned up on `ProcessingCompleted` or `ProcessingFailed`.

- [ ] **8.4 — Unit tests**
  - `SpringEventPublisherTest`: events are forwarded to Spring's `ApplicationEventPublisher`.
  - `GraphIndexingListenerTest` (with `StubGraphIndexer`): `AFTER_COMMIT` fires indexer;
    `Neo4jException` triggers retry without rolling back the transaction.
  - `SseProgressEmitterTest`: emitter receives step events; cleaned up on completion/failure.

### Completion Checklist

- [ ] Domain events published via `EventPublisher` reach their listeners.
- [ ] `GraphIndexingListener` fires after transaction commit (not during).
- [ ] Neo4j unavailability does not roll back document state.
- [ ] SSE emitter sends step-level progress without Redis or an external broker.

---

## [ ] Phase 9 — API Layer (Spring MVC)

**Goal:** Implement the Spring Boot application entry point, auth system
(Google/GitHub OAuth2 + email/password + JWT), all REST controllers, security config,
global exception handler, and SPA serving.

### Tasks

- [ ] **9.1 — `MindForgeApplication.java`** (`dev.mindforge`)
  - `@SpringBootApplication` entry point. No business logic.

- [ ] **9.2 — `SecurityConfig.java`** (`dev.mindforge.api.config`)
  - Spring Security filter chain: CSRF disabled for API paths, JWT cookie filter,
    OAuth2 login (Google, GitHub), stateless session for API endpoints.
  - JWT stored in `HttpOnly; Secure; SameSite=Lax` cookie — never in response body.
  - `BCryptPasswordEncoder` with cost 12.

- [ ] **9.3 — `JwtFilter.java`** — reads JWT from cookie, validates, sets `SecurityContext`.

- [ ] **9.4 — `AuthController.java`** (`dev.mindforge.api.controller`)
  - `POST /api/auth/register` — email/password registration.
  - `POST /api/auth/login` — email/password login; sets JWT cookie on success.
  - `POST /api/auth/logout` — clears JWT cookie.
  - `GET /api/auth/me` — returns authenticated user info (never includes `passwordHash`).
  - OAuth2 callbacks handled by Spring Security auto-config.

- [ ] **9.5 — Request/response DTOs** (`dev.mindforge.api.dto`) — all Java `record` types.
  - Never expose: `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion`, `cost`.

- [ ] **9.6 — `GlobalExceptionHandler.java`** (`@ControllerAdvice`)
  - Maps domain exceptions to HTTP status codes:
    - `LessonIdentityException` → 422
    - `IrrelevantContentException` → 422 (with `reason` field)
    - `DocumentNotFoundException` → 404
    - `AccessDeniedException` → 403
    - `DeadlineExceededException` → 503
  - All error responses use a uniform `{ "error": "...", "code": "...", "detail": "..." }` shape.

- [ ] **9.7 — REST controllers** (`dev.mindforge.api.controller`)
  - `DocumentController`: upload, list, get, delete — all verify resource ownership.
  - `KnowledgeBaseController`: CRUD for knowledge bases.
  - `ArtifactController`: get summary, flashcards, concept map for a document.
  - `PipelineController`: get pipeline status, SSE progress stream (`GET /api/docs/{id}/progress`).
  - `UserController`: profile management.
  - Every controller method: thin — input validation + auth check + delegate to service.
  - Constructor injection only — no `@Autowired` on fields.

- [ ] **9.8 — SPA serving**
  - Static Angular build served from classpath under `/static/`.
  - All non-API paths fall through to `index.html` for client-side routing.

- [ ] **9.9 — API integration tests** (`integration/api/`)
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers`.
  - Auth flow: register → login → access protected resource → logout.
  - Upload flow: upload file → poll status → verify artifact fields (no sensitive fields leaked).
  - Ownership: user A cannot access user B's documents (expects 403).

### Completion Checklist

- [ ] All controllers thin — no business logic; all delegated to application services.
- [ ] JWT stored in `HttpOnly` cookie only — never in response body.
- [ ] No sensitive fields in any API response (verified by integration tests).
- [ ] Ownership check present on every `@RestController` method.
- [ ] `mvn spring-boot:run` starts the application and serves `GET /health`.

---

## [ ] Phase 10 — Quiz and Flashcard Services

**Goal:** Implement the Quiz Service (server-authoritative session management, Graph RAG
question targeting, answer evaluation, SM-2 integration) and Flashcard Service (card
catalog, SM-2 spaced repetition scheduling).

### Tasks

- [ ] **10.1 — Add domain types** (`dev.mindforge.domain.model`)
  - `ReviewResult` record: `int rating` (0–5), `String qualityFlag`.
  - Add to domain ports: `StudyProgressStore` (get due cards, save review, due count),
    `QuizSessionStore` (create session, get session, update session, delete session).

- [ ] **10.2 — `QuizSessionStore` implementation (PostgreSQL-backed)**
  (`dev.mindforge.infrastructure.persistence`)
  - Sessions persisted to `quiz_sessions` table via Flyway migration `V8__create_quiz_sessions.sql`.
  - TTL enforced by scheduled cleanup job (`@Scheduled`).
  - Caffeine cache wraps the PostgreSQL store for low-latency reads.

- [ ] **10.3 — SM-2 algorithm** (`dev.mindforge.application.service`)
  - `SM2Scheduler` pure Java class (no framework dependencies — unit-testable in isolation).
  - Input: `ReviewResult.rating` (0–5). Output: next review interval and new ease factor.
  - Algorithm per the SM-2 specification: `EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))`.
  - `EF` minimum clamped to 1.3.

- [ ] **10.4 — `QuizService`** (`dev.mindforge.application.service`)
  - `startSession(UUID kbId, UUID userId)`: creates `QuizSession`, persists via `QuizSessionStore`.
  - `nextQuestion(UUID sessionId)`: targets weak concepts via `RetrievalPort.findWeakConcepts()`,
    fetches a question from the artifact, returns question without reference answer.
  - `submitAnswer(UUID sessionId, String answer)`: calls `QuizEvaluatorAgent` to score answer,
    updates SM-2 state via `StudyProgressStore.saveReview()`, returns score and feedback
    (never the reference answer).

- [ ] **10.5 — `FlashcardService`** (`dev.mindforge.application.service`)
  - `getDueCards(UUID kbId, UUID userId)`: returns flashcards due per SM-2 schedule.
  - `reviewCard(UUID cardId, UUID userId, int rating)`: updates SM-2 state.
  - `listCards(UUID kbId)`: returns all cards for a knowledge base.

- [ ] **10.6 — Quiz and Flashcard controllers** (added to Phase 9 controllers)
  - `QuizController`: `POST /api/quiz/sessions`, `GET /api/quiz/sessions/{id}/next`,
    `POST /api/quiz/sessions/{id}/answers`.
  - `FlashcardController`: `GET /api/flashcards`, `POST /api/flashcards/{id}/reviews`.

- [ ] **10.7 — Unit tests**
  - `SM2SchedulerTest`: rating 5 → EF increases; rating 0 → reset to 1-day interval;
    EF never drops below 1.3.
  - `QuizServiceTest` with `StubAIGateway` and `StubRetrievalAdapter`: no reference answer
    in responses.
  - `FlashcardServiceTest`: SM-2 scheduling produces correct next-review dates.

### Completion Checklist

- [ ] SM-2 algorithm produces correct scheduling for all rating values (0–5).
- [ ] Quiz responses contain no sensitive fields (`referenceAnswer`, `groundingContext`).
- [ ] Single `QuizSessionStore` implementation (PostgreSQL + Caffeine cache).
- [ ] Due card list is always scoped to `kbId` and `userId`.

---

## [ ] Phase 11 — Search and Conversational RAG

**Goal:** Implement the Search Service and Chat Service with multi-turn conversational
RAG over the knowledge base.

### Tasks

- [ ] **11.1 — Add domain types** (`dev.mindforge.domain.model`)
  - `Interaction` record: `UUID interactionId`, `UUID userId`, `UUID kbId`, `Instant startedAt`.
  - `InteractionTurn` record: `UUID turnId`, `String question`, `String answer`,
    `List<ConceptNode> usedConcepts`, `Instant createdAt`.
  - `TokenBudget` record: `int totalTokens`, `int reservedForResponse`. Computed property
    `availableForContext()`.
  - `WeakConcept` record: `String label`, `float retentionScore`.
  - Add to domain ports: `InteractionStore` (`createInteraction`, `addTurn`, `getInteraction`,
    `listForUser` — redacted, `listUnredacted`).

- [ ] **11.2 — `SearchService`** (`dev.mindforge.application.service`)
  - `search(UUID kbId, String query)`: follows retrieval priority — graph → full-text → vector.
  - Full-text via PostgreSQL `ts_query` on `content_embeddings.content`.
  - Semantic via pgvector `<=>` cosine distance on `content_embeddings.embedding`.
  - Results scoped to `kbId` — no cross-KB leakage.

- [ ] **11.3 — `ChatService`** (`dev.mindforge.application.service`)
  - `startChat(UUID kbId, UUID userId)`: creates `Interaction`, persists via `InteractionStore`.
  - `chat(UUID interactionId, String question)`:
    - Retrieves concept neighborhood for question terms.
    - Computes `TokenBudget`; trims context to fit.
    - Calls `AIGateway.complete(LARGE, grounded_prompt, INTERACTIVE)`.
    - Persists `InteractionTurn` (stores used concepts, not raw prompt/completion).
    - Returns answer — never returns `groundingContext`, `rawPrompt`, or `rawCompletion`.

- [ ] **11.4 — `InteractionStoreAdapter`** (PostgreSQL + `@InteractionStoreRedaction`)
  - `listForUser()` redacts `usedConcepts` and returns only `question` + `answer` (no RAG internals).
  - `listUnredacted()` — admin only (role check enforced).

- [ ] **11.5 — Search and Chat controllers**
  - `SearchController`: `GET /api/search?kbId=...&q=...`
  - `ChatController`: `POST /api/chat/sessions`, `POST /api/chat/sessions/{id}/messages`.

- [ ] **11.6 — Unit tests**
  - `SearchServiceTest`: result priority order (graph > full-text > vector).
  - `ChatServiceTest` with `StubAIGateway` and `StubRetrievalAdapter`: no grounding context
    in response; `TokenBudget` trimming works.
  - `InteractionStoreAdapterTest`: `listForUser` redacts internal fields.

### Completion Checklist

- [ ] Search results scoped to `kbId`.
- [ ] Chat responses never include `groundingContext`, `rawPrompt`, or `rawCompletion`.
- [ ] `TokenBudget` prevents context window overflow.
- [ ] `InteractionStore.listForUser()` returns redacted data only.

---

## [ ] Phase 12 — Angular Frontend

**Goal:** Create the Angular SPA with standalone components, lazy-loaded routing, auth
integration, and all user-facing pages for the core learning loop.

### Tasks

- [ ] **12.1 — Angular project setup**
  - `ng new frontend --standalone --routing --style=scss` inside `frontend/`.
  - Install: `@angular/material`, `cytoscape`, `@types/cytoscape`.
  - Proxy config: all `/api` requests forwarded to `:8080`.

- [ ] **12.2 — Core infrastructure**
  - `AuthService`: JWT cookie auth, Google/GitHub OAuth2 redirect flows.
  - `ApiService`: typed HTTP client wrapping all backend endpoints.
  - `AuthGuard`: redirects unauthenticated users to `/login`.
  - Error handling: global `HttpInterceptor` maps 4xx/5xx to user-visible toasts.

- [ ] **12.3 — Pages**
  - `/login` — email/password form + OAuth2 buttons.
  - `/dashboard` — knowledge base list; "New KB" and "Upload Document" actions.
  - `/kb/:id` — document list for a knowledge base; upload dropzone.
  - `/doc/:id` — document detail: summary tab, flashcards tab, concept map tab, quiz tab.
  - `/doc/:id/concept-map` — Cytoscape.js interactive concept graph.
  - `/chat` — conversational RAG chat with active knowledge base.

- [ ] **12.4 — Real-time pipeline progress**
  - `EventSourceService` subscribes to `GET /api/docs/{id}/progress` SSE stream.
  - Progress stepper component updates per `PipelineStepCompleted` event.

- [ ] **12.5 — Build integration**
  - `npm run build` output lands in `frontend/dist/frontend/browser/`.
  - `frontend-maven-plugin` copies Angular dist into `src/main/resources/static/` during
    `mvn package`, so the Spring Boot JAR serves the SPA.

### Completion Checklist

- [ ] `npm start` serves SPA on `:4200` with proxy to API `:8080`.
- [ ] `npm run build` produces output; `mvn package` embeds it in the JAR.
- [ ] All routes navigable; lazy loading confirmed by network tab.
- [ ] SSE progress updates render in the UI in real time.
- [ ] API request/response models match backend DTOs (no field name mismatches).

---

## [ ] Phase 13 — Docker and Deployment

**Goal:** Complete Docker multi-stage build, Docker Compose orchestration for local and
production, and Railway/Render deployment configuration.

### Tasks

- [ ] **13.1 — Multi-stage `Dockerfile`**
  - Stage 1 (`node:20-alpine`): `npm ci && npm run build` in `frontend/`.
  - Stage 2 (`maven:3.9-eclipse-temurin-21`): `mvn package -DskipTests` (Angular dist
    already in `src/main/resources/static/` via copy from Stage 1).
  - Stage 3 (`eclipse-temurin:21-jre-alpine`): `COPY --from=2 target/*.jar app.jar`.
  - `EXPOSE 8080`; `ENTRYPOINT ["java", "-jar", "app.jar"]`.

- [ ] **13.2 — `compose.yml`**
  - Services: `app` (the JAR image), `postgres` (PostgreSQL 15 + pgvector),
    `neo4j` (Neo4j 5 Community).
  - Health checks on all three services.
  - `app` depends on `postgres` and `neo4j` health.
  - Volume mounts for PostgreSQL and Neo4j data persistence.
  - `compose.override.yml` for local dev (mounts source, hot-reload profile).

- [ ] **13.3 — Deployment configuration**
  - `railway.json` (or `render.yaml`) specifying build command, start command, env vars.
  - `Procfile` as fallback: `web: java -jar target/mindforge.jar`.
  - Deployment docs in `docs/project/deployment.md`.

- [ ] **13.4 — Smoke test**
  - `docker build -t mindforge .` succeeds.
  - `docker compose up` starts all three services.
  - `curl http://localhost:8080/health` returns 200.
  - `curl http://localhost:8080/` returns the Angular SPA index.

### Completion Checklist

- [ ] `docker build` creates a working multi-stage image.
- [ ] `docker compose up` starts all services with passing health checks.
- [ ] Application is deployable to Railway/Render via documented procedure.

---

## [ ] Phase 14 — Observability and Tracing

**Goal:** Implement Langfuse integration for tracing LLM calls, per-operation token and
cost accounting, and alerting thresholds for LLM cost anomalies.

### Tasks

- [ ] **14.1 — Langfuse integration** (`dev.mindforge.infrastructure.ai`)
  - Wrap `AIGatewayAdapter` to emit a Langfuse trace span per `complete()` call.
  - Span includes: `modelTier`, `model`, `inputTokens`, `outputTokens`, `costUsd`, `latencyMs`.
  - Configured via `LANGFUSE_SECRET_KEY` + `LANGFUSE_PUBLIC_KEY` environment variables.
  - Disabled when env vars are absent (graceful no-op).

- [ ] **14.2 — Pipeline tracing**
  - `PipelineOrchestrator` emits a Langfuse trace per document with child spans per agent step.

- [ ] **14.3 — Cost anomaly alerting**
  - If a single pipeline run exceeds configurable cost threshold (default $0.50),
    log a `WARN` with the `documentId` and per-step cost breakdown.

### Completion Checklist

- [ ] Langfuse traces appear in Langfuse dashboard for a real pipeline run.
- [ ] Cost tracking is accurate per operation (verified against OpenRouter billing page).
- [ ] Langfuse integration is gracefully disabled when env vars absent.

---

## [ ] Phase 15 — CLI Entry Points

**Goal:** Implement utility CLI entry points for scripted pipeline runs, Neo4j backfill,
and quiz from terminal.

### Tasks

- [ ] **15.1 — `mindforge-pipeline` CLI** (`dev.mindforge.cli`)
  - Spring Boot `CommandLineRunner` activated by `--spring.profiles.active=cli`.
  - Accepts `--file <path>` and `--kb <id>`; runs the full ingestion + pipeline on a local file.
  - Prints step-level progress to stdout.

- [ ] **15.2 — `mindforge-backfill` CLI**
  - Reads all `DONE` documents from PostgreSQL; re-indexes their artifacts into Neo4j.
  - Useful for rebuilding the graph after a Neo4j wipe.

- [ ] **15.3 — `mindforge-quiz` CLI**
  - Interactive terminal quiz: loads a knowledge base, presents questions, reads answers from stdin.

### Completion Checklist

- [ ] All three CLI runners work via `java -jar mindforge.jar --spring.profiles.active=cli`.
- [ ] Backfill rebuilds Neo4j correctly from PostgreSQL data.
- [ ] CLI quiz submits answers through the same `QuizService` path as the web UI.

---

## [ ] Phase 16 — Image Analysis Agent

**Goal:** Add `ImageAnalyzerAgent` and `VISION` model-tier support to enable analysis of
images extracted from uploaded PDFs and DOCX files.

### Tasks

- [ ] **16.1 — Add domain types** (`dev.mindforge.domain.model`)
  - `ImageDescription` record: `String description`, `List<String> detectedConcepts`.
  - Add `List<ImageDescription> imageDescriptions` field to `DocumentArtifact`.

- [ ] **16.2 — PDF/DOCX image extraction**
  - Extend `PdfParser` to extract embedded images as `byte[]` + MIME type.
  - Extend `DocxParser` similarly.
  - `ContentBlock` with `BlockType.IMAGE` carries `mediaRef` pointing to extracted bytes.

- [ ] **16.3 — `ImageAnalyzerAgent`**
  - `VERSION = "1.0"`. Model tier: `VISION`.
  - For each `ContentBlock` with `BlockType.IMAGE`, calls `AIGateway.complete(VISION, ...)`.
  - Writes `List<ImageDescription>` to `DocumentArtifact`.
  - Added to the `OrchestrationGraph` as an optional parallel step alongside the LARGE-tier agents.

- [ ] **16.4 — Add `UploadSource.VISION_PIPELINE`** (if needed for tracking).

- [ ] **16.5 — Unit tests**
  - `ImageAnalyzerAgentTest` with `StubAIGateway(VISION)`.
  - `PdfParserTest`: image extraction returns non-empty `byte[]` for fixture PDF.

### Completion Checklist

- [ ] `VISION` model tier routes to correct model string.
- [ ] `ImageAnalyzerAgent` skipped (checkpoint hit) when image content unchanged.
- [ ] Image descriptions appear in `ArtifactController` response.

---

## [ ] Phase 17 — Article Fetcher Agent

**Goal:** Add `ArticleFetcherAgent` and `EgressPolicy` to optionally fetch and analyze
external articles referenced in uploaded documents.

### Tasks

- [ ] **17.1 — Add domain type** (`dev.mindforge.domain.model`)
  - `FetchedArticle` record: `String url`, `String title`, `String content`, `Instant fetchedAt`.
  - Add `List<FetchedArticle> fetchedArticles` to `DocumentArtifact`.

- [ ] **17.2 — `EgressPolicy`** (`dev.mindforge.infrastructure.security`)
  - Allowlist-based URL validator. Only URLs matching configured hostname patterns are allowed.
  - Rejects: private IP ranges (SSRF prevention), `file://`, `data:` schemes.
  - Used by `ArticleFetcherAgent` before any HTTP call.

- [ ] **17.3 — `ArticleFetcherAgent`**
  - `VERSION = "1.0"`. No direct LLM call — fetches URL, parses HTML to plain text.
  - Extracts `<a href>` URLs from document's `ContentBlock` list.
  - For each URL: validates via `EgressPolicy`, fetches via `RestTemplate` (with timeout),
    extracts readable content via an HTML-to-text utility.
  - Optional pipeline step — skipped if `ProcessingSettings.fetchExternalArticles == false`.

- [ ] **17.4 — Unit tests**
  - `EgressPolicyTest`: private IPs rejected; allowed hostnames pass; `file://` rejected.
  - `ArticleFetcherAgentTest` with a mock HTTP server: fetch succeeds; `EgressPolicy`
    violation throws `SsrfAttemptException`.

### Completion Checklist

- [ ] `EgressPolicy` rejects private IP ranges and disallowed schemes.
- [ ] Article fetcher is disabled by default (`fetchExternalArticles=false`).
- [ ] All outbound HTTP goes through `EgressPolicy` — never a raw `RestTemplate.getForObject`.

---

## [ ] Phase 18 — Discord Bot

**Goal:** Implement Discord bot with quiz, search, and upload slash commands; guild allowlists;
identity resolution; and SR reminders via DM.

### Tasks

- [ ] **18.1 — Add domain type** (`dev.mindforge.domain.port`)
  - `ExternalIdentityRepository` interface: `findUserId(String platform, String externalId)`,
    `link(UUID userId, String platform, String externalId)`,
    `createUserAndLink(String platform, String externalId, String displayName)`.
  - Add `UploadSource.DISCORD` to domain enum.

- [ ] **18.2 — `DiscordBot`** (`dev.mindforge.discord`)
  - JDA (Java Discord API) integration, started as a Spring-managed `ApplicationRunner`.
  - Slash commands: `/quiz start`, `/quiz answer`, `/search`, `/upload`.
  - Guild and role allowlist enforcement via `AppProperties.discord.*`.
  - Identity resolution: Discord user ID → internal `UUID` via `ExternalIdentityRepository`;
    auto-provisions new users on first contact.

- [ ] **18.3 — SR reminder job**
  - `@Scheduled` daily job: for each Discord-linked user with due cards, sends a DM.

- [ ] **18.4 — Unit tests**
  - Identity resolution: first contact auto-provisions user.
  - Allowlist: commands from non-allowed guilds are rejected.
  - Interaction ownership: user A cannot access user B's quiz session via slash command.

### Completion Checklist

- [ ] Bot connects and responds to slash commands in a test guild.
- [ ] Identity resolution works for new and existing Discord users.
- [ ] Commands delegated to the same application services as the web UI.
- [ ] SR reminder DMs sent correctly to users with due cards.

---

## [ ] Phase 19 — Slack Bot

**Goal:** Implement Slack bot using Slack Bolt for Java with quiz, search, and upload
handlers; workspace security; and identity resolution.

### Tasks

- [ ] **19.1 — Add `UploadSource.SLACK`** to domain enum.
- [ ] **19.2 — `SlackBot`** (`dev.mindforge.slack`)
  - Slack Bolt for Java integration via Socket Mode.
  - Commands: `/mf-quiz`, `/mf-search`, file upload handler.
  - Workspace allowlist enforcement.
  - Identity resolution via `ExternalIdentityRepository` (same as Discord).
- [ ] **19.3 — Unit tests**: allowlist enforcement, identity resolution, ownership check.

### Completion Checklist

- [ ] Bot connects via Socket Mode and responds to commands.
- [ ] Workspace allowlist enforced.
- [ ] Identity resolution reuses same `ExternalIdentityRepository` as Discord.

---

## [ ] Phase 20 — Security Hardening

**Goal:** Systematic security checklist pass against `web-security.md`, OWASP dependency
audit, and rate limiting.

### Tasks

- [ ] **20.1 — Security checklist pass**
  - Verify each requirement in `docs/standards/security/web-security.md` is implemented.
  - For each item: find the implementing code, add a comment citing the standard if non-obvious.
  - Fix any gaps found.

- [ ] **20.2 — OWASP dependency check**
  - Add `dependency-check-maven` plugin to `pom.xml`.
  - Run `mvn dependency-check:check`; fix or suppress any `CVSS >= 7.0` findings.

- [ ] **20.3 — Rate limiting**
  - Caffeine-backed rate limiter on auth endpoints (`/api/auth/login`, `/api/auth/register`).
  - Configurable: `mindforge.security.rateLimit.authEndpoints.requestsPerMinute`.
  - Returns 429 with `Retry-After` header when limit exceeded.

- [ ] **20.4 — Security integration tests**
  - Test rate limit triggers correctly.
  - Test JWT tampered cookie returns 401.
  - Test cross-user resource access returns 403.

### Completion Checklist

- [ ] Every `web-security.md` requirement traced to implementing code.
- [ ] `mvn dependency-check:check` passes (no unaddressed HIGH/CRITICAL CVEs).
- [ ] Rate limiter active on auth endpoints.

---

## [ ] Phase 21 — End-to-End Testing and CI/CD

**Goal:** Full E2E test suite covering core user journeys, GitHub Actions CI pipeline,
and quality gates enforced on PRs.

### Tasks

- [ ] **21.1 — GitHub Actions workflow** (`.github/workflows/ci.yml`)
  - Triggers: push to `main`, PRs.
  - Steps: Checkstyle → SpotBugs → `mvn test` (with Testcontainers) → JaCoCo coverage gate.
  - Fails PR if coverage drops below 70%.

- [ ] **21.2 — Playwright E2E smoke tests** (`src/test/e2e/`)
  - Tests against `docker compose up` environment.
  - Scenarios: register → upload document → wait for pipeline → view summary → start quiz
    → submit answer → view score.

- [ ] **21.3 — Architecture fitness functions**
  - ArchUnit test: no `dev.mindforge.domain` class imports from `infrastructure` or `api`.
  - ArchUnit test: all `@RestController` methods have no direct repository access.

### Completion Checklist

- [ ] CI passes on a clean checkout with real Testcontainers.
- [ ] JaCoCo coverage gate blocks PRs below 70%.
- [ ] E2E smoke test covers the full upload-to-quiz user journey.
- [ ] ArchUnit tests enforce hexagonal layer boundaries.

---

## Dependency Graph

```
Phase 0  ──► Phase 1  ──► Phase 2  ──► Phase 3
                                   │
                          Phase 4 ◄┘
                               │
                          Phase 5
                               │
                          Phase 6 ──► Phase 7 ──► Phase 8
                                                       │
                          Phase 9 ◄────────────────────┘
                               │
                     Phase 10 ─┤─ Phase 11
                               │
                          Phase 12
                               │
                          Phase 13 (DEPLOY — core system complete)
                               │
               ┌───────────────┴────────────────────┐
          Phase 14          Phase 15           Phase 16
               │                                    │
          Phase 17 ──► Phase 18 ──► Phase 19
               │
          Phase 20 ──► Phase 21
```
