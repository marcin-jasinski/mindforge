# System Architecture

## Overview

MindForge follows **Hexagonal Architecture** (Ports and Adapters). The backend is a
Spring Boot 4 application with strict layer boundaries. A separate Angular SPA frontend
communicates exclusively via the Spring MVC REST API.

Uploaded documents are **ingested into a per-knowledge-base wiki** of LLM-written pages
that compounds with every upload. Flashcards, quizzes and Query answers are cut from that
wiki, and the wiki exports as a conformant [OKF](../wiki/llm_wiki.md) bundle.

- Vocabulary: [`CONTEXT.md`](../../CONTEXT.md)
- Decisions and their reasoning: [`docs/wayfinder/okf-wiki-map.md`](../wayfinder/okf-wiki-map.md), ADRs 0010–0018

## Architecture Pattern

**Pattern**: Hexagonal (Ports and Adapters) — fullstack monorepo, single-container deployment

The domain core has zero knowledge of infrastructure. All external systems (the database, the
LLM provider, HTTP) are reached through ports (Java interfaces), with adapters wired at
composition roots.

**Layer boundaries — never cross them:**

| Layer | Package | Rule |
|---|---|---|
| **Domain** | `dev.mindforge.domain` | Pure Java, zero I/O, zero Spring/framework imports |
| **Application** | `dev.mindforge.application` | Use-case orchestration; imports only `domain` |
| **Infrastructure** | `dev.mindforge.infrastructure` | All I/O: JPA, Spring AI, parsers, export |
| **Model services** | `dev.mindforge.agent` | Stateless services that call `AIGateway`; no shared interface |
| **Adapters** | `dev.mindforge.api`, `dev.mindforge.cli` | Thin; no business logic |

## System Structure

### Domain (`dev.mindforge.domain`)

- **Wiki**: `WikiPage` (path, title, description, `PageType`, prose body, revision), `PageLink`,
  `PageSource`, `PageRevision`, `PageSupersession`, `PagePath` slug rules
- **Sources**: `Document`, `LessonIdentity`, `ContentBlock`, `ContentHash`
- **History**: `IngestRun` (kind `INGEST | REVERT | LINT`, status `RUNNING | WRITTEN | COMPLETED | FAILED`)
- **Study**: `Flashcard`, `StudyScope`, `ReviewResult`
- **Ports**: `WikiStore`, `IngestRunRepository`, `DocumentRepository`, `AIGateway`, `EventPublisher`,
  `StudyProgressStore`, `QuizSessionStore`, `InteractionStore`
- **Constraint**: every `WikiStore` method takes `kbId` as its first argument

### Application (`dev.mindforge.application`)

- `IngestionService` — upload, dedup, persist `Document`
- `IngestPipeline` — Resolve, fan-out, commit, supersede; per-knowledge-base lease and startup sweep
- `RevertService`, `LintService`, `QueryService`, `FlashcardService`, `QuizService`, `KnowledgeBaseService`
- `IndexRenderer`, `LogRenderer` — pure renderers shared by the model prompts and export

### Infrastructure (`dev.mindforge.infrastructure`)

- `persistence/` — JPA entities, Spring Data repositories, MapStruct mappers, port adapters
- `ai/` — `AIGatewayAdapter` (Spring AI, OpenRouter; Resilience4j retry + circuit breaker)
- `parsing/` — `ParserRegistry` + MIME-dispatch parsers (Markdown, PDF, DOCX, TXT)
- `event/` — `SpringEventPublisher`, in-memory SSE emitter registry
- `export/` — `BundleExporter` (zip, SnakeYAML frontmatter), `BundleConformanceValidator`
- `security/` — upload sanitizer, egress policy
- `config/` — `AppProperties` and every `@Configuration`
- `src/main/resources/prompts/pl/` — versioned Markdown prompt files

### Model services (`dev.mindforge.agent`)

Concrete services with concrete signatures. There is no `Agent` interface and no registry;
each declares a `VERSION` that is recorded on every run it takes part in.

| Service | Tier | Used by |
|---|---|---|
| `Preprocessor` | none | Ingest |
| `RelevanceGuard` | SMALL | Ingest |
| `ClaimExtractor` | LARGE | Ingest — also proposes a target path per claim |
| `PageWriter` | LARGE | Ingest — title, description, body |
| `LinkChecker` | SMALL | Ingest, Lint — proposes `LinkInsertion`s |
| `SupersessionDetector` | LARGE | Ingest |
| `WikiReviewer` | LARGE | Lint — findings and suggestions |
| `PageSelector` / `AnswerWriter` | SMALL / LARGE | Query |
| `FlashcardGenerator`, `QuizGenerator` / `QuizEvaluator` | LARGE / SMALL | Study |

### Driving Adapters

| Adapter | Path | Purpose |
|---|---|---|
| REST API | `dev.mindforge.api` | Spring MVC; JWT + OAuth2 via Spring Security |
| Angular SPA | `frontend/src/app/` | Standalone components; lazy-loaded routes |
| CLI | `dev.mindforge.cli` (Phase 15) | Pipeline runner, quiz runner |
| Discord / Slack | Phases 18–19 | `/ask`, `/quiz`, `/upload` over the same services |

## Data Flow

**Ingest:**
```
User uploads document
    ↓
DocumentController → IngestionService
    ↓  parse (ParserRegistry) → ContentBlocks
    ↓  dedup on UNIQUE (knowledge_base_id, content_hash) — identical upload returns the existing id
    ↓  persist Document + publish DocumentIngested in one transaction      [HTTP 202 Accepted]
AFTER_COMMIT → ingest worker (virtual thread)
    ↓  claim lease: UPDATE knowledge_bases SET active_run_id … WHERE active_run_id IS NULL
Preprocess → RelevanceGuard (SMALL)
    → Extract (LARGE: blocks + rendered index → claims with proposed target paths; claim cap)
    → Resolve (code: verify paths, derive new paths, build write tasks)
    → Write (LARGE ×N, parallel on virtual threads, bounded by a semaphore)
    → Link check (SMALL, in memory)
    ↓  COMMIT 1: pages, revisions, sources, links, run = WRITTEN, events
    → Supersede (LARGE ×1)
    ↓  COMMIT 2: supersessions, run = COMPLETED, lease released, events
AFTER_COMMIT → SSE progress; worker claims the next pending document of that knowledge base
```

**Query:**
```
question + prior turns + rendered index ──PageSelector (SMALL)──▶ page paths
    ──code──▶ page bodies (+1-hop links) within TokenBudget
    ──AnswerWriter (LARGE)──▶ answer citing page paths
```

**Study:** flashcards are generated lazily per Concept page (a bounded number of new pages
per session) and regenerated when the page's `revision` moves. A quiz generates one batch of
questions per session, and its reference answers live only in the server-side session row.

**Revert:** a `REVERT` run appends restore-forward revisions for every page the reverted run
still tips, and deletes that run's `page_sources` and `page_supersessions`.

**Export:** one read-only snapshot → render frontmatter, bodies, citations, `index.md`,
`log.md` → validate against OKF §9 → stream a zip.

## Data Architecture

### PostgreSQL (the only data store)

| Group | Tables |
|---|---|
| Accounts | `users`, `knowledge_bases` (+ `active_run_id` lease) |
| Sources | `documents` (one row per uploaded version of a lesson, or per conversation turn) |
| Wiki | `wiki_pages`, `page_links`, `page_revisions`, `page_sources`, `page_supersessions` |
| History | `ingest_runs` |
| Study | `flashcards`, `study_events`, `quiz_sessions` |
| Query | `interactions`, `interaction_turns` |

- History tables (`page_revisions`, `page_sources`, `page_supersessions`) reference `page_id`
  **without** an FK to `wiki_pages`, so history outlives a deleted page. Every table carries
  `knowledge_base_id` with an FK to `knowledge_bases`, so deleting a knowledge base cascades.
- `page_links` references `(knowledge_base_id, source_page_id)`, so a link cannot cross
  knowledge bases.

### Caffeine (In-Memory Cache)

- Quiz sessions only (Phase 10). There is no cache in front of the wiki: the index is one
  indexed query and bodies are primary-key reads.

There is no Neo4j, no pgvector and no object storage (ADR 0016, ADR 0014).

## The Wiki Model

- A **page** is a typed record with an opaque prose body. Identity, links, provenance and
  supersession are rows; prose is one string written only by the LLM (ADR 0010).
- **Two page types**, assigned by code: `Concept` (`concepts/<name>`) and `Source Summary`
  (`sources/<lesson-id>`). A page's **path** is its identity and never changes (ADR 0011).
- **Links are truth in the body**, stored in canonical bundle-relative form and parsed into
  `page_links`; they are resolved by join at read time. **Metadata is truth in rows** and is
  projected into frontmatter and `# Citations` on export.
- **`index.md` and `log.md` are projections.** The same index renderer feeds the model prompts,
  the SPA and export.
- **Supersession** is a row. It renders as a note under the superseded heading and never
  touches that page's prose.

## History, Revert and Deletion

- Every write appends a `PageRevision` — a post-write snapshot of title, description, type and
  body, stamped with its run. Revisions are kept forever.
- **Revisions land automatically**; there is no approval or pending state (ADR 0012).
- **Revert is per run, restore-forward, and tip-only:** it is offered for a page only while no
  later run has touched it.
- **Deletion** appends a tombstone revision and removes the page row; revert of a deletion
  re-inserts it.
- **All user edits**, deletion included, are ingest runs sourced from a conversation turn. Nobody
  hand-edits prose.

## Idempotency & Reliability

- **No step fingerprints.** A re-run is a new run against the current wiki (ADR 0015).
- **Identical upload** → dedup by constraint. **Revised upload** → a new `Document` of the same
  lesson, and a new run. There is no retraction.
- **Partial success is legal.** Failed page writes are recorded on the run; zero successful writes
  fails the run.
- **Startup sweep:** `RUNNING` → `FAILED`; `WRITTEN` → `COMPLETED` with `supersession_skipped`;
  held leases are released and pending documents are drained.
- **One page-writing run per knowledge base** (ingest, conversation edit, revert, Lint) via the
  `active_run_id` lease. Pending documents are the queue.
- **No outbox table.** A run's commit and its domain events are published in the same
  `@Transactional` boundary; listeners run `AFTER_COMMIT` and must tolerate missing an event —
  every consumer is derived or best-effort.
- **Ceiling:** the startup sweep assumes a single instance; multiple instances need lease expiry.

## Retrieval

- The **rendered index** (one line per live page: path, title, description) is the retrieval
  system. The model reads it and picks pages (ADR 0016).
- Past **20K tokens** of index, a `pg_trgm` + `simple` full-text prefilter narrows it before it
  enters a prompt.
- Vector search returns only if measurement shows the prefilter missing pages; a graph store
  returns only for traversals deeper than two hops that miss their latency budget.
- The cross-link graph (for the SPA graph view and study targeting) is `page_links`.

## Guards — every rule stated in the prompt and enforced in code

| Rule | Enforced by |
|---|---|
| Path uniqueness, links within one knowledge base, dedup, revert-of-deletion path check | Database constraints |
| One page-writing run per knowledge base | `active_run_id` lease column |
| Zero pages fails the run; claim cap fails loudly, never truncates | Service checks recorded on the run |
| A proposed target path must exist before a claim revises it | Resolve (code) |
| Sources are immutable to Ingest | A type — Write takes claims and returns a body |
| Page type, path, sources and supersession are never model-asserted | Typed fields assigned by code |
| Lint writes are link insertions only | Output type + strip-links equality check (ADR 0017) |
| Study data never reaches a page or an export | Separate tables; the exporter never reads them |

## Security & Trust Model

- **Server-authoritative state**: the server owns all grading, scoring, session and run state.
- **Client redaction**: `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion` and
  `cost` are never sent to the browser.
- **Tenancy is structural**: ownership is checked once per request in the controller;
  `WikiStore` scopes every call by `kbId`; every table carries `knowledge_base_id`.
- **Untrusted input**: filenames and external URLs are validated via `UploadSanitizer` and
  `EgressPolicy`.
- **Lesson identity**: deterministic resolution; hard reject if no valid identifier.
- **Auth enforcement**: JWT validated on every REST request via the Spring Security resource server.

## External Integrations

| Service | Role | Optional |
|---|---|---|
| Spring AI (OpenRouter / LM Studio) | Provider-agnostic LLM routing | No |
| PostgreSQL | The only data store | No |
| Caffeine | In-memory quiz session cache | No |
| Langfuse Cloud | LLM tracing + cost accounting via OTEL | Yes (falls back to logging) |

## Deployment Architecture

- **Backend**: Dockerfile — Maven builds the JAR with the embedded Angular bundle → JRE 21 slim image
- **Frontend**: deployed to Vercel separately; proxies API calls to the backend with CORS configured
- **Docker Compose (local dev)**: `api`, PostgreSQL
- **CI/CD**: GitHub Actions — build + test on PR; Vercel deploy (frontend) + Railway/Render deploy
  (backend) on merge to `main`
