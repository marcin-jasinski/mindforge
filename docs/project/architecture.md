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
| **Application** | `dev.mindforge.application` | Use-case orchestration; imports only `domain` and Spring's transaction API |
| **Infrastructure** | `dev.mindforge.infrastructure` | All I/O: JPA, Spring AI, parsers, export |
| **Model services** | `dev.mindforge.agent` | Stateless services that call `AIGateway`; no shared interface |
| **Adapters** | `dev.mindforge.api`, `dev.mindforge.cli` | Thin; no business logic |

## System Structure

### Domain (`dev.mindforge.domain`)

- **Wiki**: `WikiPage` (path, title, description, `PageType`, prose body, revision), `PageLink`,
  `PageSource`, `PageRevision`, `PageSupersession`, `PagePath`
- **Text rules**: `Identifier` (one grammar and `slugify` for page names, lesson ids and section anchors),
  `MarkdownStructure` (the one parser of sections and links), `TextRules` (single-line titles and descriptions,
  body normalisation), `TokenEstimate`
- **Sources**: `Document`, `LessonIdentity`, `ContentBlock`, `ContentHash`
- **History**: `IngestRun` (kind `INGEST | REVERT | LINT`, status `QUEUED | RUNNING | WRITTEN | COMPLETED | FAILED`)
- **Study**: `Flashcard`, `StudyScope`, `ReviewResult`
- **Ports**: `WikiStore`, `RunReportQuery`, `WikiHealthQuery`, `BundleQuery`, `IngestRunRepository`,
  `DocumentRepository`, `DocumentParser`, `UploadPolicy`, `AIGateway`, `EventPublisher`, `ProgressNotifier`,
  `StudyProgressStore`, `QuizSessionStore`, `InteractionStore`
- **Constraint**: every tenant-scoped port method takes `kbId` as its first argument; the sweep's two system methods and
  the quiz-session cleanup's `deleteExpired` are the only exceptions

### Application (`dev.mindforge.application`)

- `IngestionService` — upload; dedup and the lesson rule under the knowledge-base row lock; persist `Document` and its
  `QUEUED` run
- `RunWorker` — claim the oldest `QUEUED` run per knowledge base, dispatch it, and sweep
- `IngestPipeline` — Preprocess, Extract, Resolve, fan-out, draft checks, commit, Supersede (`Preprocessor` and the
  `HeadingChunker` are plain code in `application.ingest`)
- `RevertService`, `LintService`, `HealthService`, `QueryService`, `FlashcardService`, `QuizService`, `KnowledgeBaseService`
- `IndexRenderer`, `LogRenderer` — pure renderers shared by the model prompts and export

### Infrastructure (`dev.mindforge.infrastructure`)

- `persistence/` — JPA entities, Spring Data repositories, MapStruct mappers, port and query-port adapters
- `ai/` — `AIGatewayAdapter` (Spring AI, OpenRouter; Resilience4j retry + circuit breaker)
- `parsing/` — `ParserRegistry` (the `DocumentParser` port) + MIME-dispatch `FormatParser`s (Markdown, PDF, DOCX, TXT)
- `event/` — `SpringEventPublisher`, `SseProgressNotifier` (in-memory emitters per knowledge base)
- `export/` — `BundleExporter` (zip, SnakeYAML frontmatter), `BundleConformanceValidator`
- `security/` — upload sanitizer, egress policy
- `config/` — `AppProperties` and every `@Configuration`
- `src/main/resources/prompts/pl/` — versioned Markdown prompt files

### Model services (`dev.mindforge.agent`)

Concrete services with concrete signatures. There is no `Agent` interface and no registry;
each declares a `VERSION` that is recorded on every run it takes part in. Deterministic steps
(`Preprocessor`, Resolve) are plain code, not model services.

| Service | Tier | Used by |
|---|---|---|
| `RelevanceGuard` | SMALL | Ingest — documents only |
| `ClaimExtractor` | LARGE | Ingest — per chunk: titled claims with proposed target paths; edit items for conversation turns |
| `PageWriter` | LARGE | Ingest — description and body |
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
    ↓  parse (ParserRegistry) → ContentBlocks; resolve LessonIdentity
    ↓  lock the knowledge-base row: identical upload → existing id; lesson collision → 409 unless newVersion
    ↓  persist Document + QUEUED run + IngestRunQueued in one transaction        [HTTP 202 Accepted]
AFTER_COMMIT → RunWorker.drain(kb): oldest QUEUED run of that knowledge base
    ↓  claim: lease (active_run_id) + QUEUED → RUNNING in one transaction
Preprocess → RelevanceGuard (SMALL; skipped for conversation turns)
    → Extract (LARGE per chunk, sequential: blocks + index + pages planned so far → titled claims; claims-per-call cap)
    → Resolve (code: verify targets, derive paths, one task per path, page-task cap, edit deletions and retitles)
    → Write (LARGE ×N, parallel, one global permit pool; drafts validated; unchanged drafts dropped)
    → Link check (SMALL, in memory)
    ↓  COMMIT 1 (fenced RUNNING → WRITTEN): pages, revisions, sources, links
    → Supersede (LARGE ×1: claims + sections of Concepts one link away; proposals verified)
    ↓  COMMIT 2 (fenced WRITTEN → COMPLETED): supersessions, lease released
ProgressNotifier → SSE per knowledge base at each step and after each commit; the worker drains the next QUEUED run
```

A conversation edit is the same run: its `Document` is the chat turn, and Extract's edit prompt returns claims,
deletions and retitles instead of document claims.

**Query:**
```
question + prior turns + rendered index ──PageSelector (SMALL)──▶ page paths
    ──code──▶ page bodies (+1-hop links) within TokenBudget
    ──AnswerWriter (LARGE)──▶ answer citing page paths
```

**Study:** flashcards are generated lazily per Concept page — a bounded number of pages per session, new or stale — and
go stale when the page's content hash (title plus link-stripped body) moves. A quiz generates one batch of questions per
session, and its reference answers live only in the server-side session row.

**Revert:** a `REVERT` run, in one transaction under the lease, appends restore-forward revisions for every page the
reverted run still tips, and deletes that run's `page_sources` and `page_supersessions` for those pages only. A `REVERT`
run cannot be reverted. Removing one supersession is a `REVERT` run scoped to that row.

**Export:** one read-only snapshot → render frontmatter, bodies, supersession notes, citations, `index.md`,
`log.md` → validate against OKF §9 and the identifier grammar → stream a zip.

## Data Architecture

### PostgreSQL (the only data store)

| Group | Tables |
|---|---|
| Accounts | `users`, `knowledge_bases` (+ `active_run_id` lease) |
| Sources | `documents` (one row per uploaded version of a lesson, or per conversation turn) |
| Wiki | `wiki_pages`, `page_links`, `page_revisions`, `page_sources`, `page_supersessions` |
| History | `ingest_runs` (also the queue) |
| Study | `flashcards`, `study_events`, `quiz_sessions` |
| Query | `interactions`, `interaction_turns` |

- History tables (`page_revisions`, `page_sources`, `page_supersessions`) reference `page_id`
  **without** an FK to `wiki_pages`, so history outlives a deleted page. Every table carries
  `knowledge_base_id` with an FK to `knowledge_bases`, so deleting a knowledge base cascades.
- An FK whose two sides are removed by the same cascade (`page_sources.document_id`, `ingest_runs.document_id`,
  `ingest_runs.reverts_run_id`, every `ingest_run_id`, `documents.uploaded_by`) is `DEFERRABLE INITIALLY DEFERRED`, so
  a knowledge-base or user delete never depends on cascade order.
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
- **Titles are code's.** A Concept's title comes from the claim that created it and changes only by an explicit
  conversation retitle; a Source Summary's is its lesson title. The writer returns only description and body.
- **One grammar** (`Identifier`) for page names, lesson ids and section anchors; `slugify` transliterates and appends a
  hash where it drops a letter, so it never returns empty.
- **Sections** are level-1 headings outside fenced code. Link fragments, supersessions and flashcards address them by
  anchor. `MarkdownStructure` is the only parser of headings and links.
- **Links are truth in the body**, stored in canonical bundle-relative form (`/concepts/<id>.md#<anchor>`) and parsed
  into `page_links`; they are resolved by join at read time. Any other link to a page fails the draft; external
  `http(s)` links are allowed. **Metadata is truth in rows** and is projected into frontmatter and `# Citations` on
  export.
- **`index.md` and `log.md` are projections.** The same index renderer feeds the model prompts,
  the SPA and export.
- **Supersession** is a row. It renders as a note under the superseded heading and never
  touches that page's prose.

## History, Revert and Deletion

- Every write appends a `PageRevision` — a post-write snapshot of title, description, type and
  body, stamped with its run and carrying the page's path, which outlives the page row. Revisions are kept forever. A
  draft identical to the page writes nothing.
- **Revisions land automatically**; there is no approval or pending state (ADR 0012).
- **Revert is per run, restore-forward, and tip-only:** it is offered for a page only while no
  later run has touched it, and it removes provenance only for the pages it restores.
- **Deletion** appends a tombstone revision and removes the page row; revert of a deletion
  re-inserts it.
- **All user edits**, deletion and retitling included, are ingest runs sourced from a conversation turn. Nobody
  hand-edits prose.
- **A document ingest keeps every section** of a Concept it revises; a conversation edit may remove one.

## Idempotency & Reliability

- **No step fingerprints.** A re-run is a new run against the current wiki (ADR 0015).
- **Identical upload** → the existing id, checked under the knowledge-base row lock with the unique constraint as
  backstop. **Revised upload** → a new `Document` of the same lesson, only with an explicit `newVersion`; otherwise a
  lesson collision is 409. There is no retraction.
- **The queue is runs.** Uploads, conversation edits, retries and full Lints insert `QUEUED` runs; the worker claims the
  oldest per knowledge base. Reverts are one synchronous transaction and return 409 while a run is active.
- **One active run per knowledge base** via the `active_run_id` lease, claimed together with `QUEUED` → `RUNNING`.
- **Fencing.** Each commit updates the run only from its expected status while it holds the lease; 0 rows aborts
  without writing.
- **Partial success is legal.** A run fails only when no page task succeeded. A Supersede failure completes the run with
  `supersession_skipped`. The terminal transaction runs from a `finally`.
- **Caps fail loudly, never truncate:** claims per Extract call (retryable) and page tasks per run (not retryable).
- **Sweep** at startup and every minute, over runs not executing in this process: `WRITTEN` → `COMPLETED` +
  `supersession_skipped`; `RUNNING` → `FAILED` "interrupted", re-queued up to three attempts; leases released; queues
  drained.
- **Throttling:** one global permit pool for every background model call, across knowledge bases.
- **No outbox table.** `IngestRunQueued` is published in the transaction that queues a run; its listener runs
  `AFTER_COMMIT`, and a missed one waits for the sweep. **Progress is not a domain event** — a best-effort
  `ProgressNotifier` port streams it per knowledge base.
- **Ceiling:** one live instance. During a deploy's overlap fencing keeps runs correct; permanently multiple instances
  need a heartbeat lease.

## Retrieval

- The **rendered index** (one line per live page: path, title, description) is the retrieval
  system. The model reads it and picks pages (ADR 0016).
- Past **20K tokens** of index, a `pg_trgm` + `simple` full-text prefilter narrows it before it
  enters a prompt. Tokens are estimated as characters ÷ 3; until the prefilter exists, crossing the ceiling logs a
  warning and shows in the health view — nothing truncates the index.
- Vector search returns only if measurement shows the prefilter missing pages; a graph store
  returns only for traversals deeper than two hops that miss their latency budget.
- The cross-link graph (for the SPA graph view, study targeting and supersession candidates) is `page_links`.

## Guards — every rule stated in the prompt and enforced in code

| Rule | Enforced by |
|---|---|
| Path uniqueness, links within one knowledge base, dedup backstop, revert-of-deletion path check | Database constraints |
| One active run per knowledge base; a stale worker cannot commit | `active_run_id` lease + status-fenced commits |
| No successful page task fails the run; claims per call and page tasks per run fail loudly, never truncate | Service checks recorded on the run |
| A claim revises only a live Concept or a page planned this run; one task per path | Resolve (code) |
| Deletion and retitling only of a live Concept, only from a conversation edit | `EditItem` types + Resolve |
| Sources are immutable to Ingest | A type — Write takes claims and returns a body |
| Page type, path, title, sources and supersession are never model-asserted | Typed fields assigned by code; `PageDraft` has no title |
| Titles and descriptions are single-line and bounded; titles are escaped in structural lines | `TextRules` at write time; renderers |
| A body has no frontmatter, `# Citations`, title heading, invalid page link, image or reference definition | Draft validation over `MarkdownStructure` |
| A document ingest keeps every section of a Concept it revises | Anchor comparison before commit |
| A supersession names a section the detector was shown and a Concept this run wrote | Proposal check before commit 2 |
| Lint writes are link insertions only | Output type + eligible spans + strip-links equality (ADR 0017) |
| Every path, anchor and lesson id matches one grammar | `Identifier`, shared with the export validator |
| Study data never reaches a page or an export | Separate tables; the exporter never reads them |

## Security & Trust Model

- **Server-authoritative state**: the server owns all grading, scoring, session and run state.
- **Client redaction**: `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion` and
  `cost` are never sent to the browser.
- **Tenancy is structural**: ownership is checked once per request in the controller;
  every tenant-scoped port scopes every call by `kbId`; every table carries `knowledge_base_id`.
- **Untrusted input**: filenames and external URLs are validated via `UploadSanitizer` and
  `EgressPolicy`. Fetched articles (Phase 17, off by default) may only create pages and never supersede.
- **Rendering**: page bodies render with raw HTML escaped; external links carry `rel="noopener noreferrer nofollow"`.
- **Lesson identity**: deterministic resolution against one identifier grammar; an explicit id is never rewritten;
  hard reject if no valid identifier.
- **Retention**: a document's extracted text is kept while its knowledge base exists. The prose it produced lives on in
  page revisions, so erasure is deleting the knowledge base (ADR 0014).
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
