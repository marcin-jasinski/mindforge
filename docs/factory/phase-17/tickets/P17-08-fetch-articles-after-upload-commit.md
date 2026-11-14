---
id: P17-08
title: Fetch a new document's articles after the upload commits, on the run executor, when mindforge.articles.fetch-external-articles is on
status: done
plan_tasks: [17.2]
decisions: [D01]
depends_on: [P17-02, P17-07]
size: M
issue: 30
commit: 8ba6b0d
fix_rounds: 0
---
## Goal
`mindforge.articles.fetch-external-articles` (env `FETCH_EXTERNAL_ARTICLES`, default false) sets
`ProcessingSettings.fetchExternalArticles`. An `AFTER_COMMIT` listener on `DocumentAccepted` hands
`ArticleFetcher.fetchFor` to the virtual-thread `runExecutor`, so the 202 upload never waits for a fetch and no
failure escapes the executor task. The `ArticleFetcher` bean is wired in `@Configuration`.

## Context
- P17-02 added `DomainEvent.DocumentAccepted(documentId, knowledgeBaseId, uploadedBy, source, occurredAt)`, published
  only on the insert branch of `IngestionService.ingest`.
- P17-07 added `application/service/ArticleFetcher` (`fetchFor(kbId, documentId, uploadedBy)`, constructor takes
  `ProcessingSettings`, `DocumentRepository`, `ArticleSource`, `IngestionService` and the per-document cap) and
  `ProcessingSettings.withFetchExternalArticles(boolean)`.
- P17-05 added `infrastructure/config/ArticleFetchConfig` (beans `EgressPolicy`, `ArticleSource`) and
  `AppProperties.Articles` (`maxPerDocument`, `maxBodySize`, `connectTimeout`, `requestTimeout`).
- `infrastructure/config/PipelineConfig.java`: `processingSettings(AppProperties)` (~line 47) builds
  `ProcessingSettings.withModels(...)`; `runExecutor()` (~line 65) is the virtual-thread executor;
  `RunWorkerTriggers` bean is `@Profile("!cli")` (~line 167). `CliConfig` is `@Profile("cli")`.
- `infrastructure/event/RunWorkerTriggers.java` is the pattern: plain class, `@TransactionalEventListener(phase =
  AFTER_COMMIT)` method calling `executor.execute(...)`, bean built in a `@Configuration`.
- ADR 0024 (Accepted): the CLI process can exit before a background fetch completes; deployment notes must record the
  `cli` behaviour (P17-10 writes it). ADR 0015: listeners are best-effort and must tolerate a missed event.
- All beans via `@Configuration`, constructor injection, no static singletons (CLAUDE.md).

## Decisions to apply
**D01 — Decision:** Add `DomainEvent.DocumentAccepted` and publish it in the upload transaction (done in P17-02). An
`AFTER_COMMIT` listener in infrastructure hands the work to the virtual-thread `runExecutor`. There, an application
service extracts the URLs and fetches them with no transaction open. It then queues each article, so each article has
its own transaction. The upload request does not wait for the fetch. Retry and sweep never fetch again. With
`fetchExternalArticles` false, nothing is extracted or fetched.

**D01 — Implementation notes (this ticket's part):**
- `infrastructure/event/ArticleFetchTriggers`: `@TransactionalEventListener(phase = AFTER_COMMIT)
  onDocumentAccepted(DomainEvent.DocumentAccepted e)` calls `executor.execute(() -> articles.fetchFor(e.knowledgeBaseId(),
  e.documentId(), e.uploadedBy()))`. Wrap the whole task in a catch-all that logs (fixed text, kbId and documentId,
  exception class only), so no failure escapes the executor silently. Wire the bean in a `@Configuration` with
  `runExecutor`.
- Make an explicit, documented choice about the `cli` profile. **Ticket-writer choice:** register
  `ArticleFetchTriggers` with `@Profile("!cli")`, like `RunWorkerTriggers`, because the CLI exits after the parent run
  and would cut a background fetch off; so the CLI never fetches articles. P17-10 records this in `deployment.md`.
  `ArticleFetcher` itself may be a bean in all profiles.
- Bound the total time: the URL-count cap per document and the per-URL timeout are in `AppProperties` (P17-05).

**Q10 (answered) — binding:** add `fetchExternalArticles` (boolean, default false) to `AppProperties.Articles`;
`application.yml`: `mindforge.articles.fetch-external-articles: ${FETCH_EXTERNAL_ARTICLES:false}`;
`PipelineConfig.processingSettings` returns `withModels(...).withFetchExternalArticles(properties.getArticles()
.isFetchExternalArticles())`. No per-KB or UI toggle. Checklist "Article fetching is disabled by default" is proven
by the tests below and by P17-07 AC2.

## Acceptance criteria
- [x] AC1: With no property set, the `ProcessingSettings` bean has `fetchExternalArticles() == false`; with
  `mindforge.articles.fetch-external-articles=true` it is true.
- [x] AC2: `ArticleFetchTriggers.onDocumentAccepted` submits exactly one task to the executor, which calls
  `fetchFor(knowledgeBaseId, documentId, uploadedBy)` from the event; the listener method itself does no fetching.
- [x] AC3: When `fetchFor` throws any `RuntimeException`, the task logs it and does not rethrow.
- [x] AC4: The listener is `AFTER_COMMIT`; a rolled-back upload transaction triggers no fetch (by annotation, as in
  `RunWorkerTriggers`).
- [x] AC5: `ArticleFetchTriggers` is not a bean in the `cli` profile; `ArticleFetcher` is built in a `@Configuration`
  with `ProcessingSettings`, `DocumentRepository`, `ArticleSource`, `IngestionService` and
  `AppProperties.Articles.maxPerDocument`.
- [x] AC6: The application context starts in the default test profile and all existing integration tests pass.

## Test plan
- Unit (Mockito, direct executor `Runnable::run`, AssertJ):
  `src/test/java/dev/mindforge/unit/infrastructure/event/ArticleFetchTriggersTest.java` (new) for AC2–AC3; AC4 by an
  assertion on the method's `@TransactionalEventListener` phase (reflection) or by review.
- AC1: unit test of the binding through `Binder` + `PipelineConfig.processingSettings` (place the test in package
  `dev.mindforge.infrastructure.config` under `src/test/java/dev/mindforge/infrastructure/config/` if the method is
  package-private), or an assertion on the `ProcessingSettings` bean in an existing full-context integration test
  (e.g. under `src/test/java/dev/mindforge/integration/`). Cover both default false and set true.
- AC5–AC6 by the gate (`mvn -B verify -DskipFrontend=true`) and review.

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/event/ArticleFetchTriggers.java` (new)
- `src/main/java/dev/mindforge/infrastructure/config/ArticleFetchConfig.java`
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`
- `src/main/resources/application.yml`
- `src/test/java/dev/mindforge/unit/infrastructure/event/ArticleFetchTriggersTest.java` (new)
- a binding test as described above (new)

## Out of scope
- A durable "pending fetch" record, retry of lost fetches, a fetch on retry or sweep (D01 rejects these).
- A per-KB, per-upload or UI toggle; a fetch in the `cli` profile.
- Docs (P17-10).

## Notes
- Decided: `ArticleFetcher` and `ArticleFetchTriggers` beans live in `ArticleFetchConfig` (not `PipelineConfig`), and the constructor argument order follows the P17-07 code (`DocumentRepository, ArticleSource, IngestionService, ProcessingSettings, maxPerDocument`), because the fetch wiring then sits in one config class.
- Decided: the AC1 binding test is `src/test/java/dev/mindforge/infrastructure/config/ProcessingSettingsBindingTest.java` in package `dev.mindforge.infrastructure.config`, because `PipelineConfig.processingSettings` is package-private.
- Tester r1 AC map: AC1 ProcessingSettingsBindingTest (both cases); AC2 ArticleFetchTriggersTest.onDocumentAccepted_submitsOneTaskThatFetchesTheEventsDocument; AC3 ArticleFetchTriggersTest.aFailingFetch_*; AC4 ArticleFetchTriggersTest.onDocumentAccepted_runsOnlyAfterTheUploadCommits; AC5 ArticleFetchConfigWiringTest (ADDED, @Profile("!cli")) + ArticleFetchConfig wiring; AC6 gate (context starts, only baseline failure).
