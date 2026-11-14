---
id: P17-09
title: Prove an ARTICLE run writes nothing to a live page and skips Supersede with reason "article"
status: ready
plan_tasks: [17.2, 17.3]
decisions: []
depends_on: []
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
An integration test shows that an `ARTICLE` run whose claims target a live Concept page leaves that page's revisions
unchanged, inserts no `page_supersessions` row, and completes with `supersession_skipped = true` and a run failure
`("supersede", "article")`. No production code changes: the behaviour already exists.

## Context
- Q12 (answered, from `docs/wayfinder/tickets/28-minor-review-findings.md` § Answer (1), `Resolver.java`,
  `IngestPipeline.supersedeAndComplete`): no new pipeline code; add the test.
- `src/main/java/dev/mindforge/application/ingest/Resolver.java` (~line 60): `Resolver(index, createOnly)` drops a
  claim whose final path is live and records `dropped("claim", path, "an article may only create pages")`.
- `src/main/java/dev/mindforge/application/service/IngestPipeline.java`: `generateAndCommit` passes
  `article = uploadSource == ARTICLE` (~line 164); `supersedeAndComplete` (~line 444) adds
  `RunFailure.of("supersede", "article")` and completes with `supersession_skipped`.
- Existing unit coverage: `src/test/java/dev/mindforge/unit/application/ResolverTest.java:145`
  (`shouldDropTheClaimsOfAnArticleThatReachALivePage`). No pipeline-level ARTICLE test.
- Pattern to follow: `src/test/java/dev/mindforge/integration/application/IngestRunTest.java` (extends
  `TestContainerBase`, uses `StubAIGateway` and `support/Prompts`/`TestFixtures`, queries with `jdbc`).
- An `ARTICLE` document can be created for this test by `IngestionService.ingest(new DocumentUpload(..., 
  UploadSource.ARTICLE, ...))` with a Markdown body (the existing `ingest` accepts any `UploadSource`), so this ticket
  does not depend on the article fetcher tickets.
- Testing standard: `docs/standards/testing/test-writing.md` (Testcontainers PostgreSQL, `StubAIGateway`, AssertJ).

## Decisions to apply
No decision record applies; the operative rule is Q12's answer and plan 17.2/17.3: "an `ARTICLE` run is create-only —
Resolve drops claims that resolve to a live page — and Supersede does not run for it (`supersession_skipped`, reason
`"article"`)", and "An `ARTICLE` run whose claims target a live page creates nothing there and inserts no supersession."

## Acceptance criteria
- [ ] AC1: Given a live Concept page written by a normal (`API`) run, an `ARTICLE` run whose stubbed Extract claims
  target that page's path completes (`COMPLETED`).
- [ ] AC2: That page's revision count and current body are unchanged after the ARTICLE run.
- [ ] AC3: No `page_supersessions` row exists for the ARTICLE run.
- [ ] AC4: The ARTICLE run has `supersession_skipped = true` and a failure with step `supersede` and reason `article`.
- [ ] AC5: The run's dropped list records the claim with reason "an article may only create pages" (if the run report
  exposes it in the test's reach; else assert AC2–AC4 only and note it).
- [ ] AC6: No file under `src/main` changes.

## Test plan
- Integration (Testcontainers PostgreSQL, `StubAIGateway`): add a test method to
  `src/test/java/dev/mindforge/integration/application/IngestRunTest.java`, or a new class
  `src/test/java/dev/mindforge/integration/application/ArticleRunTest.java` extending `TestContainerBase` if
  `IngestRunTest` setup does not fit. Covers AC1–AC5. Use `make*` helpers for documents.

## Files likely touched
- `src/test/java/dev/mindforge/integration/application/IngestRunTest.java` or
  `src/test/java/dev/mindforge/integration/application/ArticleRunTest.java` (new)

## Out of scope
- Any change to `Resolver`, `IngestPipeline` or run reporting.
- Fetching articles (P17-05, P17-07, P17-08).

## Notes
