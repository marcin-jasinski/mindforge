# Development Roadmap

> Full phase-by-phase detail: [implementation-plan.md](./implementation-plan.md)
> Why the plan changed: [wiki re-cut map](../wayfinder/okf-wiki-map.md) and ADRs 0010–0018

## Current State

- **Version**: 1.0.0-SNAPSHOT
- **Completed Phases**: 0 (Scaffolding), 1 (Domain Layer), 2 (Infrastructure Foundation),
  2b (Persistence Cleanup & DTO Foundation), 3 (AI Gateway), 3b (Wiki Pivot Cleanup),
  4 (Document Parsing & Ingestion), 5 (Wiki Domain & Store), 6 (Ingest Pipeline), 7 (Lint), 9 (API Layer), 9b (Bundle Export)
- **In Progress**: None
- **Next**: Phase 10 (Quiz & Flashcard Services)

## The Wiki Re-cut (2026-09-10)

MindForge moved from per-document study artifacts to a **per-knowledge-base wiki that
compounds**, with study material cut from the wiki. Phase numbering is kept stable where a
phase's purpose survived. Where it did not:

| Phase | Change |
|---|---|
| **3b** | **New** — removes what Phases 1–2 built for the old model |
| 5 | **Re-cut** — was Agent Framework & Pipeline Orchestration; now Wiki Domain & Store |
| 6 | **Re-cut** — was seven Core Processing Agents; now the Ingest Pipeline |
| 7 | **Number reused** — Neo4j Graph Layer deleted (ADR 0016); the slot is now Lint |
| **9b** | **New** — Bundle Export |
| 11 | **Re-cut** — was Search & Conversational RAG; now Query |
| 8 | **Retired in v3.1** — progress is a port, not an event; folded into Phases 6 and 9 |
| 4, 9, 10, 12–19, 21 | Changed in place (details below) |

The 2026-09-12 spec review (map tickets T14–T29) tightened the plan without adding a phase: run queue and
fencing, supersession inputs and checks, conversation-edit entry, identifier grammar, draft validation, and
content-hash card staleness.

The plan got shorter where it mattered: two databases became one, seven agents and a DAG became
one fixed pipeline, and the checkpoint, outbox, graph-indexing and embedding machinery is gone.

---

## Core System (Phases 0–13)

Delivers a fully deployable learning platform: document ingestion into a compounding wiki with
automatic revisions and revert, Lint, OKF export, flashcards and quizzes cut from the wiki,
Query, the Angular SPA and Docker deployment.

### Foundation (Phases 0–8)

- [x] **Phase 0 — Project Scaffolding** — Maven layout, Spring Boot bootstrap, Flyway,
  `StubAIGateway` test helper. `[Effort: S]`
- [x] **Phase 1 — Domain Layer** — Core records, value objects (`ContentHash`, `LessonIdentity`),
  domain events, port interfaces. Zero framework imports. *(Parts removed in 3b.)* `[Effort: M]`
- [x] **Phase 2 — Infrastructure Foundation** — JPA entities, Flyway migrations, repository
  adapters. *(Migrations squashed in 3b.)* `[Effort: M]`
- [x] **Phase 2b — Persistence Cleanup & DTO Foundation** — `entity/`/`jpa/`/`mapper/`/`adapter/`
  sub-packages, MapStruct mappers, API DTO layer, `OpenApiConfig`. `[Effort: S]`
- [x] **Phase 3 — AI Gateway** — `AIGateway` + `AIGatewayAdapter` (Spring AI + OpenRouter),
  model tiers, deadline profiles, Resilience4j retry + circuit breaker. *(`embed` removed in 3b.)*
  `[Effort: S]`
- [x] **Phase 3b — Wiki Pivot Cleanup** *(new)* — Delete the artifact model, `Agent` abstraction,
  step fingerprints, Neo4j and pgvector wiring; fix the cross-tenant dedup lookup and Polish slugs;
  squash V1–V7 into one baseline. No new behaviour. `[Effort: S]`
- [x] **Phase 4 — Document Parsing & Ingestion** *(changed)* — `UploadSanitizer`, `ParserRegistry`,
  Markdown/PDF/DOCX/TXT parsers, heading-aware chunker, `IngestionService` with per-knowledge-base
  dedup by constraint and one `Document` per uploaded lesson version. `[Effort: M]`
- [x] **Phase 5 — Wiki Domain & Store** *(re-cut)* — Pages, paths, links, sources, revisions,
  supersessions and ingest runs; `WikiStore`; the lease; index and log renderers; restore-forward,
  tip-only revert. `[Effort: M]`
- [x] **Phase 6 — Ingest Pipeline** *(re-cut)* — Relevance guard → chunked claim extraction against the
  index → resolve → parallel page writes with draft checks → link check → commit → supersession; the run
  queue, fenced commits and the sweep; run progress over SSE; conversation edits' entry point. `[Effort: L]`
- [x] **Phase 7 — Lint** *(number reused)* — Live health checks and an on-demand full review that
  inserts links and reports findings and study suggestions. `[Effort: S]`
- **Phase 8 — Event System** *(retired in v3.1)* — Folded into Phase 6 (run event, progress port, SSE
  registry) and Phase 9 (progress endpoint). No Redis, no outbox.

### Core Product (Phases 9–12)

- [x] **Phase 9 — API Layer (Spring MVC)** *(changed)* — Security config, thin controllers for
  documents, knowledge bases, wiki pages, the page-link graph, run reports and revert, and health.
  Ownership check on every endpoint. `[Effort: L]`
- [x] **Phase 9b — Bundle Export** *(new)* — Synchronous zip of the OKF bundle from one snapshot,
  validated against OKF §9 before sending. `[Effort: S]`
- [ ] **Phase 10 — Quiz & Flashcard Services** *(changed)* — Flashcards cut lazily from Concept pages
  with content-derived identity, SM-2, server-authoritative quiz sessions targeting weak pages.
  `[Effort: M]`
- [ ] **Phase 11 — Query** *(re-cut)* — Multi-turn questions answered from wiki pages chosen via the
  index, with citations; conversation edits; page search. `[Effort: M]`
- [ ] **Phase 12 — Angular Frontend** *(changed)* — Page browser, graph view, run reports with diffs
  and revert, health view, study, chat and export. `[Effort: L]`

### Deployment (Phase 13)

- [ ] **Phase 13 — Docker & Deployment** *(changed)* — Multi-stage Dockerfile, `compose.yml` with the
  app and PostgreSQL, Railway/Render config. **Core system complete after this phase.** `[Effort: M]`

---

## Post-MVP Enhancements (Phases 14–21)

### Observability & CLI (Phases 14–15)

- [ ] **Phase 14 — Observability & Tracing** *(changed)* — Langfuse spans per LLM call and per ingest
  run; per-run cost anomaly warnings. `[Effort: S]`
- [ ] **Phase 15 — CLI Entry Points** *(changed)* — `mindforge-pipeline` (local file ingestion) and
  `mindforge-quiz`. The Neo4j backfill CLI is deleted. `[Effort: S]`

### Extended Sources (Phases 16–17)

- [ ] **Phase 16 — Image Analysis** *(changed)* — `ImageDescriber` (VISION) turns image blocks into
  text blocks before extraction. `[Effort: M]`
- [ ] **Phase 17 — Article Fetcher** *(changed)* — Referenced articles fetched through `EgressPolicy`
  become documents of their own, ingested as sources. Disabled by default. `[Effort: M]`

### Delivery Channels (Phases 18–19)

- [ ] **Phase 18 — Discord Bot** *(changed)* — `/ask`, `/quiz`, `/upload`; guild allowlist; identity
  resolution; SR reminder DMs. `[Effort: M]`
- [ ] **Phase 19 — Slack Bot** *(changed)* — `/mf-ask`, `/mf-quiz`, file upload; workspace allowlist.
  `[Effort: M]`

### Quality Gates (Phases 20–21)

- [ ] **Phase 20 — Security Hardening** — Checklist pass against `web-security.md` (now including
  export and tenancy rules), OWASP dependency check, auth rate limiting. `[Effort: S]`
- [ ] **Phase 21 — E2E Testing & CI/CD** *(changed)* — GitHub Actions, JaCoCo gate, a Playwright journey
  from upload through pages, study and export, and ArchUnit layer rules. `[Effort: M]`

---

## Technical Debt Backlog

- [ ] **English locale prompts** — Add `prompts/en/` alongside `prompts/pl/`.
- [ ] **Integration API tests** — Expand `integration/api/` to cover all endpoint paths.
- [ ] **Multi-tenant hardening** — Rate limiting and isolation review before opening to external users.

## Future Considerations (Post Phase 21)

- **Lexical prefilter** — `pg_trgm` narrowing when a knowledge base's index passes 20K tokens (ADR 0016)
- **Vector search / graph store** — only on the measured conditions named in ADR 0016
- **Git-history export** — per-run commits from `page_revisions`, if users ask for history outside the app
- **Multi-instance deployment** — a heartbeat lease for ingest runs instead of the in-process sweep set; Caffeine → Redis for sessions and rate limiting
- **Shared knowledge bases** — per-user flashcard schedules
- **Non-Latin knowledge bases** — a full transliterator (ICU4J `Any-Latin`) instead of hash-suffixed paths
- **Background flashcard generation** — if first-open latency of a large deck becomes a complaint
- **Per-document erasure** — pruning a source's revisions as well as its text, when a user asks
- **FSRS scheduling** — replace SM-2
- **Mobile frontend** — responsive layout improvements for small screens

---

*Last Updated*: 2026-09-14
*Effort Scale*: `S` 2–3 days | `M` 1 week | `L` 2+ weeks
*Reference*: [implementation-plan.md](./implementation-plan.md)
