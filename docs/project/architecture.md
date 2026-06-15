# System Architecture

## Overview

MindForge follows **Hexagonal Architecture** (Ports and Adapters). The backend is a
Spring Boot 3 application with strict layer boundaries. A separate Angular SPA frontend
communicates exclusively via the Spring MVC REST API.

## Architecture Pattern

**Pattern**: Hexagonal (Ports and Adapters) — fullstack monorepo, single-container deployment

The domain core has zero knowledge of infrastructure. All external systems (databases, LLM
providers, file systems, HTTP) are reached through abstract ports (Java interfaces), with
concrete adapters wired at composition roots. This allows swapping providers without
touching domain or application logic.

**Layer boundaries — never cross them:**

| Layer | Path | Rule |
|---|---|---|
| **Domain** | `src/main/java/.../domain/` | Pure Java, zero I/O, zero Spring/framework imports |
| **Application** | `src/main/java/.../application/` | Use-case orchestration; imports only `domain/` |
| **Infrastructure** | `src/main/java/.../infrastructure/` | All I/O: JPA, Neo4j, Spring AI, parsers, storage |
| **Agents** | `src/main/java/.../agents/` | Stateless AI agent `@Service` beans; executed via pipeline |
| **Adapters** | `src/main/java/.../api/` | Thin Spring MVC `@RestController`s; no business logic |

## System Structure

### Domain Layer (`src/main/java/.../domain/`)
- **Purpose**: Core business entities, value objects, domain events, and port interfaces
- **Key classes**: entity records (`Document`, `KnowledgeBase`, `DocumentArtifact`),
  port interfaces, domain events, `LessonIdentity` value object, `StepFingerprint`
- **Constraint**: No imports from `infrastructure`, `application`, or any Spring/framework class

### Application Layer (`src/main/java/.../application/`)
- **Purpose**: Use-case orchestration — coordinates domain objects and port interfaces
- **Key classes**: `PipelineOrchestrator`, `QuizService`, `ChatService`, `SearchService`,
  `FlashcardService`, `IngestionService`, `KnowledgeBaseService`
- **Constraint**: Imports only `domain/`; never imports JPA entities or Spring AI directly

### Infrastructure Layer (`src/main/java/.../infrastructure/`)
- **Purpose**: All I/O adapters implementing domain ports
- **Sub-components**:
  - `persistence/` — Spring Data JPA repositories; `@Entity` classes; Hibernate mappings
  - `graph/` — Spring Data Neo4j repositories (derived projection, rebuilt from PostgreSQL)
  - `ai/` — Spring AI `ChatClient` wrappers; structured output converters; pgvector store
  - `ai/prompts/pl/` — versioned Markdown prompt templates (Polish locale)
  - `parsing/` — `ParserRegistry` + MIME-dispatch parsers (Markdown, PDF, DOCX, TXT)
  - `events/` — Outbox pattern for at-least-once delivery to Neo4j and SSE consumers
  - `security/` — Upload sanitizer, egress policy (allowlisted outbound HTTP)
  - `storage/` — `StoragePort` implementation: filesystem (dev) / S3 (prod)
  - `config/` — `AppProperties` (@ConfigurationProperties); loaded once at startup

### AI Agents (`src/main/java/.../agents/`)
- **Purpose**: Stateless AI agent `@Service` beans executed by the pipeline
- **Agents**: `SummarizerAgent`, `FlashcardGeneratorAgent`, `QuizGeneratorAgent`,
  `QuizEvaluatorAgent`, `ConceptMapperAgent`, `ImageAnalyzerAgent`, `PreprocessorAgent`,
  `RelevanceGuardAgent`, `ArticleFetcherAgent`
- **Pattern**: Each agent is open/closed — new agents registered without modifying the orchestrator

### Driving Adapters

| Adapter | Path | Purpose |
|---|---|---|
| REST API | `src/main/java/.../api/` | Spring MVC; JWT + OAuth2 via Spring Security 6 |
| Angular SPA | `frontend/src/app/` | Standalone components; lazy-loaded routes; deployed to Vercel |
| Discord Bot | _(Phase 2 — deferred)_ | Guild allowlist; slash commands |
| Slack Bot | _(Phase 2 — deferred)_ | Interaction ownership enforcement |
| CLI | _(Phase 2 — deferred)_ | Pipeline runner, quiz runner, backfill |

## Data Flow

```
User uploads document
    ↓
REST controller (api/DocumentController.java)
    ↓
Application ingestion service (application/IngestionService.java)
    ↓  [returns PipelineJob ID immediately; HTTP 202 Accepted]
Background virtual thread (@Async PipelineExecutor)
    ↓
Parser (infrastructure/parsing/) → ContentBlocks
    ↓
Pipeline orchestrator (application/pipeline/PipelineOrchestrator.java)
    ↓  [step fingerprint check — skip if unchanged]
AI agents (agents/) via Spring AI ChatClient (infrastructure/ai/)
    ↓  [checkpoint each step output to document_artifacts]
DocumentArtifact persisted to PostgreSQL
    ↓  [outbox event emitted; SseEmitter notified]
Outbox relay → Neo4j graph projection updated (infrastructure/graph/)
```

**Query flow:**
```
Frontend / Discord / Slack
    ↓
API router
    ↓
Application service (search / quiz / chat / flashcards)
    ↓
Graph traversal first → full-text/lexical second → vector embeddings last
    ↓
Response (with server-authoritative state; never exposes raw prompts or reference answers)
```

## Data Architecture

### PostgreSQL (Single Source of Truth)
- All business data: users, documents, artifacts, quiz interactions, chat history
- `document_artifacts` table: JSONB column stores full `DocumentArtifact` including step fingerprints
- `pgvector` extension: embedding vectors for semantic search stored alongside business data
- Outbox table: `pipeline_events` for at-least-once delivery

### Neo4j (Derived Projection)
- Concept nodes and relationships derived from `DocumentArtifact.conceptMap`
- Rebuilt entirely from PostgreSQL on demand — never a source of truth
- Used for concept neighborhood queries and knowledge graph visualization

### Caffeine (In-Memory Cache)
- Query result caching and computed aggregations
- No external service; appropriate for single-instance deployment

## Security & Trust Model

- **Server-authoritative state**: Server owns all grading, scoring, and session state
- **Client redaction**: `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion` never sent to browser
- **Untrusted input**: All filenames and external URLs validated via `UploadSanitizer` and `EgressPolicy`
- **Lesson identity**: Deterministic resolution; hard reject if no valid identifier — never falls back to `"unknown"`
- **Auth enforcement**: JWT validated on every REST request via Spring Security resource server

## Idempotency & Reliability

- **Step fingerprinting**: Each pipeline step checksums its inputs + prompt version + model ID; unchanged steps are skipped on reruns
- **Outbox pattern**: Events written transactionally to `pipeline_events`; relay delivers at-least-once to Neo4j
- **Composition roots**: One per runtime surface — no static singletons, no field injection, dependencies explicit via constructor

## External Integrations

| Service | Role | Optional |
|---|---|---|
| Spring AI (OpenRouter / LM Studio) | Provider-agnostic LLM routing | No |
| PostgreSQL | Primary data store + pgvector embeddings | No |
| Neo4j | Graph read projection | Yes (degrades gracefully) |
| Caffeine | In-memory cache | No |
| Filesystem / S3 | Document file storage | No |
| Langfuse Cloud | LLM tracing + cost accounting via OTEL | Yes (falls back to logging) |

## Deployment Architecture

- **Backend**: Dockerfile — Maven builds JAR with embedded Angular bundle → JRE 21 slim image
- **Frontend**: Deployed to Vercel separately; proxies API calls to backend with CORS configured
- **Docker Compose (local dev)**: `api`, PostgreSQL, Neo4j
- **CI/CD**: GitHub Actions — build + test on PR; Vercel deploy (frontend) + Railway/Render deploy (backend) on merge to `main`

---
*Based on architecture decisions finalised 2026-05-26*
*ADRs*: [docs/adr/0001-java-spring-boot-rewrite.md](../adr/0001-java-spring-boot-rewrite.md) | [docs/adr/0002-spring-mvc-virtual-threads.md](../adr/0002-spring-mvc-virtual-threads.md)
