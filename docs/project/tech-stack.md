# Technology Stack

## Overview

MindForge is a fullstack monorepo with a Java backend and Angular SPA frontend.
The frontend is deployed to Vercel; the backend runs as a Docker container on Railway/Render.
The stack prioritizes correctness, AI cost discipline, strict architectural boundaries,
and developer reviewability — every line of code must be understandable by the sole developer.

---

## Languages

### Java (21 LTS)
- **Usage**: ~100% of backend codebase
- **Rationale**: Developer-reviewable code is the primary constraint; Java 21 LTS provides
  virtual threads for async concurrency without reactive complexity, records for immutable
  domain objects, and a mature ecosystem the developer can fully audit
- **Key features used**: virtual threads, records, sealed interfaces, pattern matching
  for switch, text blocks, `SequencedCollection`

### TypeScript (~5.9.2)
- **Usage**: 100% of frontend codebase
- **Rationale**: Type safety for API contract adherence; generated types from OpenAPI spec
  via `openapi-typescript` keep frontend in sync with backend schemas automatically

---

## Frameworks

### Backend

| Framework | Version | Rationale |
|---|---|---|
| **Spring Boot** | 4.1 | Auto-configuration, production-ready defaults; virtual threads via one config line |
| **Spring MVC** | (bundled) | Synchronous-style HTTP handlers running on virtual threads; readable stack traces |
| **Spring AI** | 2.0 | Provider-agnostic LLM access; structured output; pgvector vector store integration |
| **Spring Security** | 6.x | OAuth2 login (Google / GitHub) + JWT issuance for Angular API calls |
| **Spring Data JPA** | 3.x | Hibernate ORM; `@Entity` classes isolated in `infrastructure/`; domain uses records |
| **Spring Data Neo4j** | 7.x | Cypher repositories for concept graph traversal |
| **Flyway** | 10.x | SQL-first schema migrations; `V1__init.sql` versioned files |

### Frontend

| Framework | Version | Rationale |
|---|---|---|
| **Angular** | ^21.2.0 | Standalone components, signals, lazy-loaded routing; mature toolchain |
| **Angular Material + CDK** | ^21.2.7 | Consistent UI component library; accessible by default |
| **Cytoscape.js** | ^3.x | Interactive force-directed graph visualization for concept maps |
| **RxJS** | ~7.8.0 | Reactive streams for HTTP and SSE event handling |

### Testing

| Framework | Version | Context |
|---|---|---|
| **JUnit 5** | 5.x | Java test runner; unit / integration / e2e structure |
| **Mockito** | 5.x | Mocking framework; domain and application unit tests use no Spring context |
| **Testcontainers** | 1.x | Real PostgreSQL + Neo4j containers in integration tests — no DB mocks |
| **AssertJ** | 3.x | Fluent assertions: `assertThat(doc).extracting(Document::status).isEqualTo(DONE)` |
| **Vitest** | ^4.0.x | Frontend test runner |
| **jsdom** | ^28.0.0 | DOM simulation for Vitest frontend tests |

---

## Databases

### PostgreSQL (15+)
- **ORM**: Spring Data JPA + Hibernate; `@Entity` classes live in `infrastructure/persistence/`
- **Extension**: `pgvector` — embedding vectors stored alongside business data via Spring AI's
  `PgVectorStore`; no separate vector database required
- **Role**: Single source of truth for all business data
- **Features used**: JSONB (artifact storage), `gen_random_uuid()`, full-text search

### Neo4j (5.x)
- **Driver**: Spring Data Neo4j 7.x
- **Role**: Derived read projection of concept/document relationships; rebuilt from
  PostgreSQL artifacts via outbox events — never a source of truth
- **Rationale**: Native graph traversal for concept neighborhood queries; can be fully
  rebuilt from PostgreSQL artifacts at any time

---

## Cache

### Caffeine
- **Usage**: In-memory application cache (query results, computed aggregations)
- **Rationale**: No Redis service dependency; appropriate for single-instance personal
  deployment. Redis can be introduced later if multi-instance deployment is needed.

---

## Object Storage

### Filesystem abstraction (Spring `Resource`)
- **Dev**: Local filesystem (`/data/uploads/`)
- **Prod**: S3-compatible via `software.amazon.awssdk:s3` — swap by changing configuration
- **Rationale**: Eliminates MinIO service dependency; the `StoragePort` interface in the
  domain layer makes the backing implementation transparent to application logic

---

## AI & LLM

### Spring AI (2.0)
- **Role**: Provider-agnostic LLM access with structured output and vector store integration
- **Default provider**: OpenRouter (`https://openrouter.ai/api/v1`) — access to GPT-4o,
  Claude, Llama, and others through a single API key
- **Local dev**: LM Studio (`http://localhost:1234/v1`) — OpenAI-compatible, no API key needed
- **Provider swap**: Single env var `SPRING_AI_OPENAI_BASE_URL` — no code changes required
- **Model tiers**: `SMALL` (cheap/fast classification), `LARGE` (synthesis/generation),
  `VISION` (image analysis)
- **Cost discipline**: Deterministic logic first → SMALL model → LARGE model last

### Langfuse (Cloud)
- **Role**: LLM observability — per-call token/cost accounting, prompt tracing
- **Integration**: Spring AI → Micrometer Tracing → OpenTelemetry export → Langfuse Cloud
- **Fallback**: Standard SLF4J logging when `LANGFUSE_PUBLIC_KEY` is absent

---

## Auth

| Component | Library | Notes |
|---|---|---|
| OAuth2 login | Spring Security 6 `oauth2Login()` | Google + GitHub providers |
| JWT issuance | `jjwt-api` / `jjwt-impl` | Issued after OAuth2 callback for Angular API calls |
| JWT validation | Spring Security `oauth2ResourceServer()` | Validates JWT on every REST request |

---

## Document Parsing

| Format | Library | Notes |
|---|---|---|
| Markdown | `flexmark-java` | YAML frontmatter extraction for `lesson_id`, `title` |
| PDF | Apache PDFBox | Text extraction with metadata (Title field for lesson identity) |
| DOCX | Apache POI | Word document parsing |
| TXT | Built-in | Plain text ingestion |

Registry pattern: `ParserRegistry` dispatches by MIME type — open/closed for new formats.

---

## Build Tools & Package Management

| Tool | Context |
|---|---|
| **Maven 3.9+** | Java build, dependency management, test execution |
| **frontend-maven-plugin** | Runs `npm install` + `npm build` as part of Maven lifecycle |
| **npm 11.x** | Frontend package management |
| **@angular/build ^21.2.x** | Angular application builder |
| **Prettier ^3.x** | Frontend code formatting |

---

## Development Tools

### Linting & Formatting
- **Checkstyle / Spotless** — Java code style enforcement via Maven plugin
- **ESLint + Prettier** — Frontend TypeScript formatting

### Type Safety
- Java records for all domain objects (compile-time immutability, no setter leakage)
- TypeScript strict mode on frontend

### API Type Generation
- **springdoc-openapi-starter-webmvc-ui** — generates `/v3/api-docs` from Spring MVC controllers
- **openapi-typescript ^7.6.1** — generates TypeScript types from OpenAPI spec;
  run after schema changes to keep `frontend/src/app/core/models/api.models.ts` in sync

---

## Infrastructure

### Containerization
- Single Dockerfile: Maven builds JAR (with Angular bundle via `frontend-maven-plugin`)
  → JRE 21 slim image runs the Spring Boot API
- Docker Compose (local dev): `api`, PostgreSQL, Neo4j

### CI/CD
- **GitHub + GitHub Actions**: build + test on every PR (Maven verify + Angular test + Playwright E2E)
- **Frontend**: Vercel — automatic deploy on merge to `main`; preview URLs on every PR branch
- **Backend**: Railway or Render — Docker container deploy on merge to `main`

---

## Key Dependencies Summary

| Artifact (Maven) | Purpose |
|---|---|
| `spring-boot-starter-web` | Spring MVC + virtual threads |
| `spring-boot-starter-data-jpa` | Hibernate ORM |
| `spring-boot-starter-data-neo4j` | Graph projection repositories |
| `spring-boot-starter-security` | OAuth2 login + JWT resource server |
| `spring-ai-openai-spring-boot-starter` | LLM access (OpenAI-compatible API) |
| `spring-ai-pgvector-store-spring-boot-starter` | pgvector embeddings in PostgreSQL |
| `flyway-core` | SQL schema migrations |
| `testcontainers` | Real database containers in integration tests |
| `caffeine` | In-memory caching |
| `jjwt-api` + `jjwt-impl` | JWT issuance after OAuth2 login |
| `flexmark-all` | Markdown + frontmatter parsing |
| `pdfbox` | PDF text + metadata extraction |
| `poi-ooxml` | DOCX parsing |
| `cytoscape` (npm) | Interactive concept map visualization |
| `openapi-typescript` (npm) | Frontend type generation from OpenAPI spec |

---

*Last Updated*: 2026-05-26
*Stack*: Java 21 / Spring Boot 4.1 / Angular 21 / PostgreSQL + pgvector / Neo4j / Spring AI
