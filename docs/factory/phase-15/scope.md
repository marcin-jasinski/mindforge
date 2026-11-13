# Phase 15 — CLI Entry Points: scope

## Goal

Utility CLI entry points for scripted ingestion and terminal quizzes
(`docs/project/implementation-plan.md#phase-15--cli-entry-points`).

## In scope

- 15.1 `mindforge-pipeline`: a `CommandLineRunner` in `dev.mindforge.cli`, active under
  `--spring.profiles.active=cli`, taking `--file <path> --kb <id>`. It ingests through the same `IngestionService` and
  ingest pipeline as an upload, and prints step progress.
- 15.2 `mindforge-quiz`: an interactive terminal quiz through the same `QuizService` as the web UI.
- The wiring that makes a `cli` profile start (profile yml, beans that must or must not exist in that profile).
- Tests for both runners, and doc updates listed in the factory config (`implementation-plan.md`, `roadmap.md`,
  `deployment.md`, `INDEX.md` only if a doc is added, `CLAUDE.md` "Project State" paragraph).

## Out of scope

- `mindforge-backfill` (Neo4j rebuild): deleted in v3.0 (plan Phase 15 note; ADR-0016).
- A CLI for Query, flashcards, Lint, revert or export — the plan names only the two runners.
- A heartbeat lease or any change that lets two live instances run permanently (`deployment.md#one-live-instance`,
  ADR-0015 Consequences/Amendments: "permanently multiple instances need a heartbeat lease").
- Phase 14's open manual checklist item (Langfuse traces for a real run). Phase 15 has no real dependency on Phase 14:
  the dependency graph puts 14, 15 and 16 in parallel after Phase 13, and the code Phase 15 calls (`RunWorker`,
  `RunMeter`, `TracingGateway`) is already merged and works with Langfuse keys absent.
- Discord / Slack (Phases 18–19), file watcher (no plan task; `UploadSource.FILE_WATCHER` is an unused enum value).

## Existing state

- No `dev.mindforge.cli` package, no `CommandLineRunner`/`ApplicationRunner`, no `@Profile` anywhere, no
  `application-cli.yml`. Only `application.yml` and `application-dev.yml` exist (`src/main/resources/`).
- `src/main/java/dev/mindforge/MindForgeApplication.java`: plain `SpringApplication.run`; one boot path for web.
- Ingest is asynchronous. `IngestionService.ingest(DocumentUpload)` (`application/service/IngestionService.java`)
  admits, parses, dedups under the KB row lock, inserts the `Document` and a `QUEUED` run, publishes
  `IngestRunQueued`, and returns the document id (or the id of an identical document). The run executes later in
  `RunWorker.drain` (`application/service/RunWorker.java`), woken by `RunWorkerTriggers`
  (`infrastructure/event/RunWorkerTriggers.java`): AFTER_COMMIT of the event, a sweep on `ApplicationReadyEvent`, and a
  sweep every `mindforge.runs.sweep-interval` (PT1M).
- The sweep settles every `RUNNING`/`WRITTEN` run not in this process's in-memory set: it fails `RUNNING` runs as
  interrupted and re-queues ingests. A second process (a CLI) therefore interferes with a live server
  (`deployment.md#one-live-instance`). See Q01.
- Progress: `ProgressNotifier` port (`domain/port/ProgressNotifier.java`), one implementation `SseProgressNotifier`
  (`infrastructure/event/SseProgressNotifier.java`), injected by its concrete type into `IngestPipeline`,
  `LintService`, `RunWorker` and others in `infrastructure/config/PipelineConfig.java`. `RunProgress` carries
  `step`, `done`, `total`. Step progress is not persisted on `ingest_runs`. See Q05.
- `DocumentUpload` record (`application/service/DocumentUpload.java`) needs `uploadedBy` (FK to `users`, NOT NULL in
  `V1__baseline.sql`), `UploadSource`, filename, MIME type, bytes, optional `lessonId`, `newVersion`.
- MIME type: `UploadSanitizer` admits only the MIME types `ParserRegistry.mimeTypes()` has a parser for
  (`text/markdown`, `text/plain`, `application/pdf`, DOCX). The SPA maps `.md`/`.markdown`/`.txt` itself
  (`frontend/src/app/pages/kb/documents.ts#mimeType`). See Q07.
- Quiz: `QuizService` (`application/service/QuizService.java`) has `begin` (async; the web path via `QuizController`),
  `start` (sync; used only by unit tests), `isPreparing`, `next`, `answer`. Ownership: `KnowledgeBaseService.requireOwner`
  in each controller method; `QuizService.owned` checks the session's user. `QuizEvaluation(score, feedback)` has no
  reference answer. Scope mapping is `api/controller/StudyScopes` (package-private, in the API adapter).
- Web-only beans with no profile guard: `api/config/SecurityConfig` (`@EnableWebSecurity`, `HttpSecurity`),
  `SpaConfig`, `RateLimitConfig`/`RateLimitInterceptor`, `ApiDocsConfig`, controllers. See Q02.
- Test layout: `src/test/java/dev/mindforge/{unit,integration}/...`, `support/StubAIGateway`, `StubAiConfig`,
  `TestContainerBase`, `TestFixtures`.

## Constraints that apply

- Adapters `dev.mindforge.cli` are thin, no business logic; may import all layers
  (`CLAUDE.md` Architecture table; `docs/standards/architecture/hexagonal.md`; `docs/project/architecture.md`).
- All Spring beans wired via `@Configuration`; constructor injection; no static singletons (`CLAUDE.md`).
- Never call a model inside a database transaction; a run's commit and its events in one transaction
  (`CLAUDE.md`; ADR-0015).
- Every tenant-scoped port method takes `kbId` first (`CLAUDE.md`; `hexagonal.md`).
- One live instance; the sweep treats a run in another process as abandoned (`docs/project/deployment.md#one-live-instance`;
  ADR-0015 Consequences and 2026-09-12 Amendment). A CLI that becomes a second live worker contradicts this.
- Never expose `reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`, `cost`; study data never enters
  a wiki page (`CLAUDE.md` Security; `docs/standards/security/web-security.md`). The quiz CLI must not print the
  reference answer or the grounding excerpt.
- Ownership is verified for every resource access (`CLAUDE.md`; `web-security.md`) — the CLI must act as a definite user
  that owns the KB (see Q04).
- Never modify `ParserRegistry` to add a format (`CLAUDE.md`).
- Logger named `log`; `record` for value types; typed domain exceptions (`docs/standards/backend/java-conventions.md`).
- Minimal implementation: no future stubs, no speculative abstractions (`docs/standards/global/minimal-implementation.md`).
- Tests: unit tests without Spring context; `StubAIGateway`; Testcontainers PostgreSQL for integration; AssertJ;
  `make*` factories (`CLAUDE.md`; `docs/standards/testing/test-writing.md`).
- Vocabulary from `CONTEXT.md` (Knowledge Base, Ingest Run, Quiz Session, Study Scope, Document, Lesson).

## Definition of done

- [ ] 15.1 `mindforge-pipeline` CLI implemented (`--file <path> --kb <id>`, same `IngestionService` and pipeline,
      prints step progress).
- [ ] 15.2 `mindforge-quiz` CLI implemented (interactive, same `QuizService` as the web UI).
- [ ] Both CLI runners work via `java -jar mindforge.jar --spring.profiles.active=cli`.
- [ ] CLI quiz submits answers through the same `QuizService` path as the web UI.
- [ ] Docs updated: plan tasks ticked, roadmap, `deployment.md` (how to run the CLI, constraint vs. a live server),
      CLAUDE.md "Project State".
- [ ] All quality gates pass (`mvn -B verify -DskipFrontend=true`), apart from the known baseline failure
      `ApiFlowTest.theLoginRouteIsTheSinglePageApp` (404 with `-DskipFrontend`).
