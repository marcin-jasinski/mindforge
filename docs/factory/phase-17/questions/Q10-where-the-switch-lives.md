---
id: Q10
status: answered
impact: low
blocks: [W05]
answered_by: docs/project/implementation-plan.md#Phase 17 — Article Fetcher (17.2); docs/wayfinder/tickets/28-minor-review-findings.md#Answer (1); docs/project/architecture.md#Security & Trust Model
---
## Question
Is article fetching switched per knowledge base, per upload, or globally, and what is its default?

## Context
`ProcessingSettings` is one global bean built from `AppProperties` in `PipelineConfig.processingSettings`. It has an
unused `featureFlags` map and `isEnabled(String)`, but no `fetchExternalArticles`.

## Known options
- Settled: global, off by default. Plan 17.2 and T28 #1 name `ProcessingSettings.fetchExternalArticles`; architecture
  says "Fetched articles (Phase 17, off by default)". Bind it from a `mindforge.*` property (env var documented in
  `docs/project/deployment.md`) into `ProcessingSettings`, default `false`. Whether it is a record component or a
  `featureFlags` entry is a local choice; a named component matches the plan's wording. No per-KB or UI toggle.

## Constraints
Checklist: "Article fetching is disabled by default" — prove it with a test on the default settings and with a test
that a document with URLs fetches nothing when the setting is off.
