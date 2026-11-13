---
id: D05
question: Q05
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q05.r1.tally.md]
adr:
---
## Decision
Use A: in the `cli` profile a `dev.mindforge.cli.ConsoleProgressNotifier implements ProgressNotifier` replaces
`SseProgressNotifier`. Change the `IngestPipeline`, `LintService` and `RunWorker` bean methods in `PipelineConfig` to
take the `ProgressNotifier` port, not the concrete class. Put `@Profile("!cli")` on the `SseProgressNotifier` bean and
register the console bean in the `@Profile("cli")` CLI configuration.

## Why
- D01 runs the ingest in the CLI JVM and requires the CLI to print step progress; step progress is not persisted, so
  only an in-process notifier can deliver it. C cannot (verified hard objection).
- The three pipeline beans take `SseProgressNotifier` by concrete type (PipelineConfig.java:125, :136, :144). Typing
  them as the domain port is a type-only change and removes a small hexagonal leak.
- Under `cli` there is no HTTP server (D02), so the SSE emitter map has no subscribers; one notifier per profile is
  enough. B adds a listener registry with one consumer (minimal-implementation.md).
- Smallest change, fully reversible.

## Rejected
- B: fan-out registry and listener lifecycle for a single consumer; speculative.
- C: status polling only; cannot print steps, fails plan 15.1 and D01.

## Dissent
None. Risk to watch: `RunController` takes `SseProgressNotifier` by concrete type (RunController.java:41) and D02 does
not gate controllers up front. Without the SSE bean under `cli`, the cli context fails at startup. Second risk: drain
can claim a run that is not the CLI's own (D01), so output must not be misattributed.

## Implementation notes
- `PipelineConfig`: in `ingestPipeline`, `lintService`, `runWorker` change the parameter type
  `SseProgressNotifier progressNotifier` to `ProgressNotifier progressNotifier`. Keep the bean method
  `SseProgressNotifier progressNotifier()` with its concrete return type and add `@Profile("!cli")`.
- `RunController`: add `@ConditionalOnWebApplication(type = SERVLET)` (D02's fallback rule; it is the only controller
  that needs the SSE bean). Keep its concrete `SseProgressNotifier` parameter. If more controllers fail the cli
  context-load test, follow D02 (one package-level condition, not class by class).
- New `dev.mindforge.cli.ConsoleProgressNotifier implements ProgressNotifier`: constructor takes a `PrintStream`
  (default `System.out` in the bean method). `notify(kbId, progress)` prints one line per `RunProgress`, for example
  `[<first 8 chars of runId>] <kind> <step> <done>/<total> <status>`, filters to the CLI's run when a run id is set
  (a `volatile UUID` set by the pipeline runner after `ingest` returns, through a `watch(UUID runId)` method), and
  wraps everything in `try/catch (RuntimeException)` so it never throws.
- Bean `ConsoleProgressNotifier` in the `@Profile("cli")` CLI `@Configuration` (no component scan, no static singleton).
- Tests: plain unit test (no Spring) with a `ByteArrayOutputStream` PrintStream: line format; filters out another
  runId once `watch` is set; does not throw when the PrintStream throws / `progress` fields are null. The D02 cli
  context-load test asserts the single `ProgressNotifier` bean is a `ConsoleProgressNotifier` and that no
  `SseProgressNotifier` / `RunController` bean exists. Existing web tests stay green (the SSE bean is still the only
  `ProgressNotifier` in the default profile).
