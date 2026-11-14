---
id: Q12
status: answered
impact: low
blocks: [W06]
answered_by: docs/wayfinder/tickets/28-minor-review-findings.md#Answer (1); src/main/java/dev/mindforge/application/ingest/Resolver.java; src/main/java/dev/mindforge/application/service/IngestPipeline.java#supersedeAndComplete
---
## Question
Does 17.2's "an `ARTICLE` run is create-only … Supersede does not run" need new pipeline code, or only the test 17.3
asks for?

## Context
`Resolver(index, createOnly)` drops claims whose final path is live and records
`dropped("claim", path, "an article may only create pages")`; `IngestPipeline.supersedeAndComplete` adds
`RunFailure.of("supersede", "article")` and completes with `supersession_skipped = true`. `ResolverTest` covers the
resolver; no test covers a whole ARTICLE run.

## Known options
- Settled: no new pipeline code. Add the integration test (beside `IngestRunTest`, Testcontainers + `StubAIGateway`):
  an ARTICLE document whose claims target a live Concept leaves that page's revisions unchanged, inserts no
  `page_supersessions` row, and completes with `supersession_skipped` and failure reason `"article"`.

## Constraints
Integration tests use real PostgreSQL via Testcontainers and `StubAIGateway` (`test-writing.md`).
