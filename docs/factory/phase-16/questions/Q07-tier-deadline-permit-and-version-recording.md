---
id: Q07
status: answered
impact: low
blocks: [W04, W05]
answered_by: docs/standards/backend/ai_agents.md#Model Selection; docs/standards/backend/ai_agents.md#Version Management; docs/adr/0004-spring-ai-openrouter.md#Consequences
---
## Question
Which tier, deadline profile and permit pool does `ImageDescriber` use, and how is it recorded on the run?

## Context
Plan 16.2 names `VISION` and a `VERSION` constant. Other INGEST services use `DeadlineProfile.BACKGROUND` and are
wired with `PipelineConfig.background(...)` (permit pool + `TracingGateway` named after the service).

## Known options
- Settled: `public static final ModelTier TIER = ModelTier.VISION` (ADR 0004: VISION is the image-analysis tier);
  `DeadlineProfile.BACKGROUND`; wired through `PipelineConfig.background(..., ImageDescriber.class)`; recorded with
  `state.version(ImageDescriber.class, ImageDescriber.VERSION, ImageDescriber.TIER)` when at least one image is
  described; added to `ModelServiceVersionTest`.

## Constraints
"Calls made by an `INGEST` or `LINT` run take a permit from the global background semaphore first" and "`VERSION` and
the resolved model id of every call are recorded on the run in `ingest_runs.step_versions`" (`ai_agents.md`).
