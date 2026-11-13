---
id: Q05
status: decided
decision: D05
impact: medium
blocks: [W02, W03]
---
## Question
How does the pipeline CLI receive the run's step progress to print it?

## Context
- Plan 15.1: "prints step progress".
- `RunProgress(runId, kind, documentId, status, step, done, total, at)` is sent to the `ProgressNotifier` port; the only
  implementation, `SseProgressNotifier`, is a bean injected by its concrete type into `IngestPipeline`, `LintService`,
  `RunWorker` (`infrastructure/config/PipelineConfig.java`) and the SSE endpoint.
- Step progress is not persisted; `ingest_runs` holds status, failures, step versions only.
- Depends on Q01: options below assume the run executes in the CLI process (Q01-A). Under Q01-B only status polling
  is possible.

## Known options
- A: In the `cli` profile, a console `ProgressNotifier` (in `dev.mindforge.cli`) replaces `SseProgressNotifier`; the
  pipeline wiring is changed to take the `ProgressNotifier` port type instead of the concrete class.
- B: A fan-out notifier that sends to SSE and to registered listeners; the CLI registers a listener for its kb id.
- C: The CLI polls `IngestionService.document(kbId, documentId)` and prints status changes only (no steps).

## Constraints
- `ProgressNotifier` is best-effort and never throws (port javadoc).
- No speculative abstractions (minimal-implementation.md); `@Configuration` wiring only (CLAUDE.md).
- Domain port must stay JDK-only (hexagonal.md).
