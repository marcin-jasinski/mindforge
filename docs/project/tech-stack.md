# Technology Stack

## Overview

MindForge is a fullstack monorepo with a Java backend and Angular SPA frontend.
The frontend is deployed to Vercel; the backend runs as a Docker container on Railway/Render.
The stack prioritizes correctness, AI cost discipline, strict architectural boundaries,
and developer reviewability — every line of code must be understandable by the sole developer.
It deliberately has **one data store**: PostgreSQL.

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
| **Spring AI** | 2.0 | Provider-agnostic LLM access behind `AIGateway`; chat completions only |
| **Spring Security** | 6.x | OAuth2 login (Google / GitHub) + JWT issuance for Angular API calls |
| **Spring Data JPA** | 3.x | Hibernate ORM; `@Entity` classes isolated in `infrastructure/`; domain uses records |
| **Flyway** | 10.x | SQL-first schema migrations; one pre-deployment baseline, forward-only after it |

### Frontend

| Framework | Version | Rationale |
|---|---|---|
| **Angular** | ^21.2.0 | Standalone components, signals, lazy-loaded routing; mature toolchain |
| **Angular Material + CDK** | ^21.2.7 | Consistent UI component library; accessible by default |
| **Cytoscape.js** | ^3.x | Interactive force-directed graph of the wiki's page links |
| **RxJS** | ~7.8.0 | Reactive streams for HTTP and SSE event handling |

### Testing

| Framework | Version | Context |
|---|---|---|
| **JUnit 5** | 5.x | Java test runner; unit / integration / e2e structure |
| **Mockito** | 5.x | Mocking framework; domain and application unit tests use no Spring context |
| **Testcontainers** | 1.x | Real PostgreSQL containers in integration tests — no DB mocks |
| **AssertJ** | 3.x | Fluent assertions: `assertThat(page).extracting(WikiPage::revision).isEqualTo(2)` |
| **Vitest** | ^4.0.x | Frontend test runner |
| **jsdom** | ^28.0.0 | DOM simulation for Vitest frontend tests |

---

## Databases

### PostgreSQL (15+)
- **ORM**: Spring Data JPA + Hibernate; `@Entity` classes live in `infrastructure/persistence/`
- **Role**: The only data store — accounts, documents, the wiki and its full revision history,
  ingest runs, study state, and Query interactions
- **Features used**: JSONB (content blocks, run failures and findings, quiz sessions), partial
  unique indexes (upload dedup), composite foreign keys (tenancy), `UPDATE … WHERE` lease claims,
  `REPEATABLE READ` snapshots (export), full-text search
- **Held in reserve**: the `pg_trgm` extension, added only when a knowledge base's rendered index
  passes the 20K-token prefilter ceiling (ADR 0016)

**Removed** (ADR 0016): Neo4j — the page-link graph is `page_links` in PostgreSQL — and
pgvector — the rendered index is the retrieval system. Both have named re-add conditions.

---

## Cache

### Caffeine
- **Usage**: In-memory cache for quiz sessions (Phase 10). Nothing caches the wiki: the index is
  one indexed query and bodies are primary-key reads.
- **Rationale**: No Redis service dependency; appropriate for single-instance personal
  deployment. Redis can be introduced later if multi-instance deployment is needed.

---

## Object Storage

None. Uploaded documents are parsed at upload and stored as extracted text and content blocks in
PostgreSQL; the original bytes are not retained, and the wiki bundle is rendered only on export
(ADR 0014). Add object storage if a feature ever needs the original upload bytes.

---

## AI & LLM

### Spring AI (2.0)
- **Role**: Provider-agnostic chat completions behind the `AIGateway` port
- **Default provider**: OpenRouter (`https://openrouter.ai/api/v1`) — access to GPT-4o,
  Claude, Llama, and others through a single API key
- **Local dev**: LM Studio (`http://localhost:1234/v1`) — OpenAI-compatible, no API key needed
- **Provider swap**: Single env var `SPRING_AI_OPENAI_BASE_URL` — no code changes required
- **Model tiers**: `SMALL` (classification, link checks, page selection), `LARGE` (claim extraction,
  page writing, supersession, study generation, answers), `VISION` (image description)
- **Cost discipline**: Deterministic code first → SMALL model → LARGE model last; every call single-shot

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
| Markdown | `flexmark-java` | YAML frontmatter extraction for `lesson_id`, `title`; also parses page links |
| PDF | Apache PDFBox | Text extraction with metadata (Title field for lesson identity) |
| DOCX | Apache POI | Word document parsing |
| TXT | Built-in | Plain text ingestion |

Registry pattern: `ParserRegistry` dispatches by MIME type — open/closed for new formats.

## Bundle Export

| Concern | Library | Notes |
|---|---|---|
| Archive | `java.util.zip` (JDK) | Zip of the rendered OKF bundle |
| Frontmatter | SnakeYAML (shipped with Spring Boot) | Emission with correct escaping, and re-parsing for the conformance check |

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
- Docker Compose (local dev): `api`, PostgreSQL

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
| `spring-boot-starter-security` | OAuth2 login + JWT resource server |
| `spring-ai-starter-model-openai` | LLM access (OpenAI-compatible API) |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | SQL schema migrations |
| `testcontainers-postgresql` | Real database containers in integration tests |
| `caffeine` | In-memory caching |
| `resilience4j-retry` + `resilience4j-circuitbreaker` | AI gateway resilience (ADR 0009) |
| `jjwt-api` + `jjwt-impl` | JWT issuance after OAuth2 login |
| `flexmark-all` | Markdown + frontmatter parsing |
| `pdfbox` | PDF text + metadata extraction |
| `poi-ooxml` | DOCX parsing |
| `cytoscape` (npm) | Interactive page-link graph visualization |
| `openapi-typescript` (npm) | Frontend type generation from OpenAPI spec |

---

*Last Updated*: 2026-09-10
*Stack*: Java 21 / Spring Boot 4.1 / Angular 21 / PostgreSQL / Spring AI
