# Phase 17 — Article Fetcher: scope

## Goal

From `docs/project/implementation-plan.md` § "Phase 17 — Article Fetcher": optionally fetch external articles that
uploaded documents refer to, safely, and ingest them. A fetched article is a source of its own — a `Document` ingested
as its own run — so it gets dedup, provenance and revert unchanged.

## In scope

- 17.1 `EgressPolicy` in `dev.mindforge.infrastructure.security`: an allowlist-based URL validator that rejects
  private IP ranges, `file://` and `data:`.
- 17.2 `ArticleFetcher` (no LLM): extract URLs from a document's blocks, validate each through `EgressPolicy`, fetch
  with a timeout, convert HTML to text. Each article becomes a `Document` (`UploadSource.ARTICLE`, lesson identity from
  the article title) in the same knowledge base, with a `QUEUED` run like any upload. An article whose lesson id
  already exists is skipped and logged (T18).
- 17.2 trust restriction: an `ARTICLE` run is create-only and Supersede does not run for it (T28). The code already
  does this (see "Existing state"); this phase adds the test the plan asks for.
- 17.2 switch: disabled unless `ProcessingSettings.fetchExternalArticles` is true; off by default.
- The wiring the plan does not name: the configuration property, the `@Configuration` beans, and the trigger that
  calls the fetcher (Q01).
- 17.3 tests: `EgressPolicyTest`; `ArticleFetcherTest` with a mock HTTP server, including a policy violation; an
  `ARTICLE` run whose claims target a live page creates nothing there and inserts no supersession.
- Close-out docs (factory `docs_to_update`): implementation plan, roadmap, `docs/INDEX.md` if a description changes,
  `docs/project/deployment.md` (new settings), CLAUDE.md "Project State"; also `docs/project/architecture.md` (data
  flow and trust model), `docs/project/tech-stack.md` if a dependency is added, and an ADR if Q01/Q03 make a
  load-bearing decision.

## Out of scope

- A per-knowledge-base or per-upload toggle: the plan names `ProcessingSettings`, which is global (Q10).
- Fetching links found inside fetched articles (no recursion), and links in conversation turns (Q11).
- Images in fetched articles, and fetching image URLs (Phase 16 decided images come from PDF/DOCX bytes only).
- New versions of an article lesson: nobody is there to confirm one (T18).
- Discord/Slack bots (18–19), security hardening (20), E2E/CI (21), Phase 14's open manual Langfuse check.
- Frontend changes: articles show in the document list as documents already do (`DocumentResponse` has no
  `uploadSource`; the list filters only `CONVERSATION`). No UI switch for the setting.

## Existing state (code is the evidence)

- `UploadSource.ARTICLE` exists, with javadoc "fetched from a reference and its run may only create pages":
  `src/main/java/dev/mindforge/domain/model/UploadSource.java`.
- **Create-only is already built.** `Resolver(index, createOnly)` drops a claim whose final path is live and records
  `dropped("claim", path, "an article may only create pages")`:
  `src/main/java/dev/mindforge/application/ingest/Resolver.java` (~line 60). `IngestPipeline.generateAndCommit` passes
  `article = uploadSource == ARTICLE` (~line 164).
- **Supersede skip is already built.** `IngestPipeline.supersedeAndComplete` (~line 444): for an article it adds
  `RunFailure.of("supersede", "article")` and completes with `supersession_skipped = true`.
- Tests: only `ResolverTest.shouldDropTheClaimsOfAnArticleThatReachALivePage`
  (`src/test/java/dev/mindforge/unit/application/ResolverTest.java:145`). No pipeline-level test for an ARTICLE run;
  pipeline tests are integration tests (`src/test/java/dev/mindforge/integration/application/IngestRunTest.java`).
- **No `EgressPolicy`, no `ArticleFetcher`, no HTTP client** in `src/main`. `infrastructure/security/` holds
  `UploadSanitizer`, `JwtService`, `BCryptPasswordHasher`. `architecture.md` already lists "egress policy" under
  `security/`, and `web-security.md` shows `egressPolicy.check(externalUrl)`.
- **No HTML parser.** `pom.xml` has commonmark, pdfbox, poi-ooxml; no jsoup. `ParserRegistry` registers Markdown,
  PDF, DOCX, plain text (`infrastructure/config/IngestionConfig.java`); `UploadSanitizer` admits exactly the
  registered MIME types, so registering `text/html` would also make HTML uploadable (Q05).
- `ProcessingSettings` (`domain/model/ProcessingSettings.java`) has a `featureFlags` map and `isEnabled(flag)`; neither
  is used anywhere. It has no `fetchExternalArticles`. `PipelineConfig.processingSettings` builds it from
  `AppProperties` with `withModels(...)` only.
- `AppProperties` has no article or egress section.
- `IngestionService.ingest(DocumentUpload)` (`application/service/IngestionService.java`): admit → parse → lesson
  identity → hash → one transaction under the knowledge-base row lock (identical → existing id; lesson exists and not
  `newVersion` → `LessonAlreadyExistsException`; insert Document + QUEUED run + `IngestRunQueued`). An article that
  reuses this path gets the lesson rule as an exception to catch and log, and dedup for free.
- `DomainEvent` has one event, `IngestRunQueued`; `RunWorkerTriggers` drains on it `AFTER_COMMIT`
  (`infrastructure/event/RunWorkerTriggers.java`). There is no event for "document accepted" or "run completed".
- `documents.source_filename` is `VARCHAR(1000) NOT NULL` (`V1__baseline.sql`); `PageRenderer.documentLines` renders it
  in a page's citations. A URL fits (Q07).
- `MarkdownParser` keeps a non-heading block's Markdown source, so link URLs stay in block text; PDF/DOCX blocks carry
  only visible text (a DOCX hyperlink's target is not in the text).
- Outbound HTTP today: Spring AI to `OPENROUTER_BASE_URL` (dev profile: `http://localhost:1234/v1`), OTLP to Langfuse,
  OAuth2 provider calls. None goes through any egress check (Q03).
- No partial work, stubs or TODOs for `EgressPolicy` or `ArticleFetcher` in `src/`.

## Plan/code disagreements

1. 17.2 lists create-only and Supersede skip as work; the code already has both (from Phase 6). Only the test is
   missing.
2. Checklist "All outbound HTTP goes through `EgressPolicy`" vs 17.1 "rejects private IP ranges": the model provider in
   the dev profile is `localhost:1234`, so a literal reading breaks local development (Q03).
3. 17.3 asks for `ArticleFetcherTest` "with a mock HTTP server": a mock server listens on loopback, which the policy
   must reject. The test needs a seam (Q13).
4. The plan names `ProcessingSettings.fetchExternalArticles` as a field; the record has an unused `featureFlags` map
   instead. Either form satisfies the plan (Q10).

## Constraints that apply

From `CLAUDE.md` (Non-Negotiable Rules) unless stated otherwise:

- Domain is JDK only; any new port or value type in `dev.mindforge.domain` uses no framework or I/O class
  (`docs/standards/architecture/hexagonal.md`). Application imports only domain, agent, Spring transaction API and
  SLF4J — so HTTP and HTML parsing live in infrastructure behind a port (Q09).
- All Spring beans wired via `@Configuration`; constructor injection; no static singletons.
- Never modify `ParserRegistry` to add a format — register a parser instead (only relevant if Q05 picks a parser).
- The ingest pipeline is closed: a new step is a design change (ADR 0013). If Q01 places fetching inside the run, it
  needs an ADR 0013 amendment.
- A run's commit and its domain events in one `@Transactional` boundary; listeners run after commit and must tolerate
  a missed event; no outbox (ADR 0015).
- Never do network I/O inside a database transaction (same reasoning as "never call a model inside a database
  transaction"; `IngestionService` parses outside its transaction).
- Every tenant-scoped port method takes `kbId` first.
- Untrusted input: external URLs go through `EgressPolicy`; never ad-hoc HTTP (`docs/standards/security/web-security.md`
  § "Untrusted Input Handling"). Allowlists over blocklists (`docs/standards/global/validation.md`).
- Untrusted articles: disabled by default; create-only; no Supersede; existing lesson id → skip and log
  (`docs/wayfinder/tickets/28-minor-review-findings.md` #1; `docs/wayfinder/tickets/18-lesson-and-path-identity.md`
  § 1; `docs/project/architecture.md` § "Security & Trust Model").
- Lesson identity: deterministic, one `Identifier` grammar, never fall back to a placeholder
  (`docs/standards/backend/ai_agents.md` § "Lesson Identity").
- Study data never enters a page or an export (unchanged).
- Domain exceptions, never bare `RuntimeException`; fixed failure text on a run, never raw provider/remote messages
  (`docs/standards/global/error-handling.md`; `IngestRunFailedException.reasonFor`).
- Logger `log`; records for value types; `sealed interface` for unions (`docs/standards/backend/java-conventions.md`).
- Minimal implementation: no speculative abstractions, no future stubs
  (`docs/standards/global/minimal-implementation.md`).
- Minimal dependencies; document why a new one is included (`docs/standards/global/conventions.md`;
  `docs/project/tech-stack.md` "Key Dependencies Summary").
- Tests: unit tests without a Spring context; Mockito for ports; AssertJ; `make*` factories; Testcontainers for
  integration (`docs/standards/testing/test-writing.md`).
- Integration tests need Docker; do not skip them (`.claude/factory.md`).

## Definition of done

Plan completion checklist (Phase 17):

- [ ] All outbound HTTP goes through `EgressPolicy` (with the scope Q03 decides).
- [ ] Article fetching is disabled by default.

Plus: tasks 17.1–17.3 are `[x]` in the plan and the phase heading is `[x]`; roadmap, deployment, architecture,
CLAUDE.md "Project State" and the other `docs_to_update` files reflect the phase; all quality gates pass
(`mvn -B verify -DskipFrontend=true`; the frontend gate only if `frontend/**` changes). The known baseline failure
`ApiFlowTest.theLoginRouteIsTheSinglePageApp` (gate run without frontend, per `journal.md`) is not a regression.
