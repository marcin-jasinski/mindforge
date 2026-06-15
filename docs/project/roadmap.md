# Development Roadmap

> Full phase-by-phase detail: [implementation-plan.md](./implementation-plan.md)

## Current State

- **Version**: 1.0.0-SNAPSHOT (greenfield — implementation not started)
- **Completed Phases**: None
- **Remaining Phases (0–21)**: All phases pending

---

## Core System (Phases 0–13)

Delivers a fully deployable, agentic learning platform: document ingestion, 7-agent AI
pipeline with checkpointing, knowledge graph, quiz/flashcard engine with SM-2, RAG chat,
Angular SPA, and Docker deployment.

### Foundation (Phases 0–8)

- [ ] **Phase 0 — Project Scaffolding** — Maven multi-module layout, Spring Boot bootstrap,
  Flyway, Docker Compose for local dev, `StubAIGateway` test helper. `[Effort: S]`
- [ ] **Phase 1 — Domain Layer** — Core entities, value objects (`ContentHash`, `LessonIdentity`),
  domain events (sealed interface), `Agent` interface, port interfaces. Zero framework imports.
  Only types needed for ingestion are defined here; others added phase-by-phase. `[Effort: M]`
- [ ] **Phase 2 — Infrastructure Foundation** — JPA entities, Flyway migrations (V1–V7
  including pgvector), Spring Data JPA repository adapters. `[Effort: M]`
- [ ] **Phase 3 — AI Gateway** — `AIGateway` interface + `AIGatewayAdapter` (Spring AI +
  OpenRouter), model-tier routing (SMALL/LARGE), deadline profiles, `StubAIGateway`. `[Effort: S]`
- [ ] **Phase 4 — Document Parsing & Ingestion** — `UploadSanitizer`, MIME-dispatch
  `ParserRegistry`, Markdown/PDF/DOCX/TXT parsers, heading-aware chunker,
  `IngestionService` with deduplication and revision management. `[Effort: M]`
- [ ] **Phase 5 — Agent Framework & Pipeline Orchestration** — `Agent` interface, `AgentRegistry`,
  `OrchestrationGraph`, `PipelineOrchestrator` with step-fingerprint checkpointing and
  DAG-aware invalidation, background virtual-thread worker. `[Effort: M]`
- [ ] **Phase 6 — Core Processing Agents** — 7 agents: `PreprocessorAgent`, `RelevanceGuardAgent`
  (SMALL — filters non-learning content), `SummarizerAgent`, `FlashcardGeneratorAgent`,
  `ConceptMapperAgent`, `QuizGeneratorAgent`, `QuizEvaluatorAgent`. Each declares `VERSION`
  constant; all tested with `StubAIGateway`. `[Effort: L]`
- [ ] **Phase 7 — Neo4j Graph Layer** — Spring Data Neo4j derived projection, `GraphIndexer`
  adapter, Cypher queries for concept neighborhoods and weak-concept detection,
  `StubRetrievalAdapter`. `[Effort: M]`
- [ ] **Phase 8 — Event System** — `@TransactionalEventListener` for Neo4j indexing after
  commit; in-memory `SseEmitter` registry for pipeline progress updates. No Redis, no outbox
  relay. `[Effort: S]`

### Core Product (Phases 9–12)

- [ ] **Phase 9 — API Layer (Spring MVC)** — `SecurityConfig` (JWT in HttpOnly cookies,
  OAuth2 for Google/GitHub, BCrypt cost 12), 8 thin `@RestController`s, `GlobalExceptionHandler`,
  SPA serving. Ownership check on every endpoint. `[Effort: L]`
- [ ] **Phase 10 — Quiz & Flashcard Services** — Server-authoritative quiz sessions
  (single `QuizSessionStore` backed by PostgreSQL + Caffeine cache), SM-2 spaced repetition
  scheduler, `QuizService` with Graph RAG question targeting, `FlashcardService`. `[Effort: M]`
- [ ] **Phase 11 — Search & Conversational RAG** — Full-text + pgvector semantic search,
  multi-turn `ChatService` with `TokenBudget` management and grounding-context redaction.
  Chat domain types (`Interaction`, `InteractionTurn`, `TokenBudget`, `WeakConcept`) added
  here (not in Phase 1). `[Effort: M]`
- [ ] **Phase 12 — Angular Frontend** — Angular 21 SPA with Angular Material, Cytoscape.js
  concept map, Signals, standalone components, SSE progress stepper, full routing. `[Effort: L]`

### Deployment (Phase 13)

- [ ] **Phase 13 — Docker & Deployment** — Multi-stage Dockerfile (Node → Maven → JRE),
  `compose.yml` with health checks for all services, Railway/Render deployment config.
  **Core system complete after this phase.** `[Effort: M]`

---

## Post-MVP Enhancements (Phases 14–21)

All features below exist in the design — they are deferred until the core system is
running and deployed, so effort is focused on demonstrating the learning loop first.

### Observability & CLI (Phases 14–15)

- [ ] **Phase 14 — Observability & Tracing** — Langfuse integration: trace spans per LLM call
  and per pipeline run, per-operation cost tracking, cost anomaly alerting. Graceful no-op
  when Langfuse env vars absent. `[Effort: S]`
- [ ] **Phase 15 — CLI Entry Points** — `mindforge-pipeline` (local file ingestion),
  `mindforge-backfill` (rebuild Neo4j from PostgreSQL), `mindforge-quiz` (terminal quiz
  using the same `QuizService` as the web UI). `[Effort: S]`

### Extended Agents (Phases 16–17)

- [ ] **Phase 16 — Image Analysis Agent** — `ImageAnalyzerAgent` with VISION model tier,
  PDF/DOCX image extraction, `ImageDescription` domain type added to `DocumentArtifact`.
  `[Effort: M]`
- [ ] **Phase 17 — Article Fetcher Agent** — `ArticleFetcherAgent` (fetches external URLs
  referenced in documents), `EgressPolicy` (SSRF prevention via allowlist + private-IP
  blocking), `FetchedArticle` domain type. Disabled by default. `[Effort: M]`

### Delivery Channels (Phases 18–19)

- [ ] **Phase 18 — Discord Bot** — JDA-based Discord integration: `/quiz`, `/search`,
  `/upload` slash commands; guild allowlist; identity resolution via
  `ExternalIdentityRepository`; SR reminder DMs. `[Effort: M]`
- [ ] **Phase 19 — Slack Bot** — Slack Bolt for Java via Socket Mode: `/mf-quiz`,
  `/mf-search`, file upload handler; workspace allowlist; shared `ExternalIdentityRepository`
  with Discord. `[Effort: M]`

### Quality Gates (Phases 20–21)

- [ ] **Phase 20 — Security Hardening** — Security checklist pass against
  `docs/standards/security/web-security.md`, OWASP dependency-check Maven plugin
  (block CVSS ≥ 7.0), Caffeine-backed rate limiter on auth endpoints. `[Effort: S]`
- [ ] **Phase 21 — E2E Testing & CI/CD** — GitHub Actions (Checkstyle + SpotBugs + Testcontainers
  on PRs, JaCoCo 70% coverage gate), Playwright E2E smoke tests for the upload-to-quiz
  journey, ArchUnit fitness functions for hexagonal layer boundaries. `[Effort: M]`

---

## Technical Debt Backlog

- [ ] **English locale prompts** — Add `prompts/en/` alongside `prompts/pl/`; Polish-only
  prompts limit non-Polish users.
- [ ] **Integration API tests** — The `integration/api/` test directory starts minimal;
  expand to cover all endpoint paths.
- [ ] **Multi-tenant hardening** — Currently designed for personal use; rate limiting and
  tenant isolation need review before opening to external users.

## Future Considerations (Post Phase 21)

- **Knowledge graph export**: JSON-LD / RDF export of concept maps
- **Mobile frontend**: Responsive layout improvements for small screens
- **Multi-instance deployment**: Caffeine → Redis upgrade for distributed rate limiting and
  distributed `QuizSessionStore`
- **Full transactional outbox**: If multiple independent event consumers emerge, replace
  `@TransactionalEventListener` with a proper outbox table + relay
- **FSRS scheduling**: Replace SM-2 with the more accurate FSRS algorithm

---

*Last Updated*: 2026-06-15
*Effort Scale*: `S` 2–3 days | `M` 1 week | `L` 2+ weeks
*Reference*: [implementation-plan.md](./implementation-plan.md)
