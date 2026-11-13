---
id: P15-02
title: Print run progress to the console in the cli profile
status: ready
plan_tasks: [15.1]
decisions: [D05]
depends_on: [P15-01]
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
In the `cli` profile the single `ProgressNotifier` bean is a `dev.mindforge.cli.ConsoleProgressNotifier` that prints one
line per `RunProgress` to a `PrintStream`, optionally only for one watched run. The pipeline beans depend on the
`ProgressNotifier` port, not on `SseProgressNotifier`. The default (web) profile still uses `SseProgressNotifier`.

## Context
- Port: `src/main/java/dev/mindforge/domain/port/ProgressNotifier.java` — `void notify(UUID kbId, RunProgress progress)`;
  best-effort, never throws.
- `src/main/java/dev/mindforge/domain/model/RunProgress.java`: `record RunProgress(UUID runId, RunKind kind,
  UUID documentId, RunStatus status, String step, Integer done, Integer total, Instant at)`; `step` is null on a status
  change; `done`/`total` are set only while pages are written.
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`: bean `SseProgressNotifier
  progressNotifier()`; `ingestPipeline(...)`, `lintService(...)` and `runWorker(...)` take `SseProgressNotifier
  progressNotifier` by concrete type.
- `src/main/java/dev/mindforge/api/controller/RunController.java` takes `SseProgressNotifier` by concrete type; without
  the SSE bean the cli context fails at startup unless the controller is gated.
- After P15-01: `application-cli.yml` exists, web config is `@ConditionalOnWebApplication`, and
  `integration/cli/CliProfileContextTest` boots the `cli` context.
- There is no `dev.mindforge.cli` package yet; this ticket creates it with the `@Profile("cli")` configuration class.
- Rules: `cli` adapters are thin and may import all layers; beans wired in `@Configuration`, constructor injection, no
  static singletons; logger named `log` (`docs/standards/backend/java-conventions.md`).

## Decisions to apply
**D05 — Decision:** In the `cli` profile a `dev.mindforge.cli.ConsoleProgressNotifier implements ProgressNotifier`
replaces `SseProgressNotifier`. Change the `IngestPipeline`, `LintService` and `RunWorker` bean methods in
`PipelineConfig` to take the `ProgressNotifier` port, not the concrete class. Put `@Profile("!cli")` on the
`SseProgressNotifier` bean and register the console bean in the `@Profile("cli")` CLI configuration.

**D05 — Implementation notes:**
- `PipelineConfig`: in `ingestPipeline`, `lintService`, `runWorker` change the parameter type `SseProgressNotifier
  progressNotifier` to `ProgressNotifier progressNotifier`. Keep the bean method `SseProgressNotifier
  progressNotifier()` with its concrete return type and add `@Profile("!cli")`.
- `RunController`: add `@ConditionalOnWebApplication(type = SERVLET)` (D02's fallback rule; it is the only controller
  that needs the SSE bean). Keep its concrete `SseProgressNotifier` parameter. If more controllers fail the cli
  context-load test, follow D02 (one package-level condition, not class by class).
- New `dev.mindforge.cli.ConsoleProgressNotifier implements ProgressNotifier`: constructor takes a `PrintStream`
  (`System.out` in the bean method). `notify(kbId, progress)` prints one line per `RunProgress`, for example
  `[<first 8 chars of runId>] <kind> <step> <done>/<total> <status>`, filters to the CLI's run once a run id is set (a
  `volatile UUID` set through a `watch(UUID runId)` method, called by the pipeline runner after `ingest` returns), and
  wraps everything in `try/catch (RuntimeException)` so it never throws.
- Bean `ConsoleProgressNotifier` in the `@Profile("cli")` CLI `@Configuration` (`dev.mindforge.cli.CliConfig`; no
  component scan of the notifier, no static singleton).
- Tests: plain unit test (no Spring) with a `ByteArrayOutputStream` PrintStream: line format; filters out another
  runId once `watch` is set; does not throw when the PrintStream throws or `progress` fields are null. The cli
  context-load test asserts the single `ProgressNotifier` bean is a `ConsoleProgressNotifier` and that no
  `SseProgressNotifier` / `RunController` bean exists. Existing web tests stay green (the SSE bean is still the only
  `ProgressNotifier` in the default profile).

## Acceptance criteria
- [ ] AC1: `PipelineConfig.ingestPipeline`, `lintService` and `runWorker` take `ProgressNotifier`, not
      `SseProgressNotifier`.
- [ ] AC2: `ConsoleProgressNotifier.notify` prints one line per `RunProgress` with short run id, kind, step (omitted or
      blank when null), `done/total` (only when both are set) and status.
- [ ] AC3: Before `watch(runId)` is called, every run's progress is printed; after `watch(runId)`, progress of any
      other run id is not printed.
- [ ] AC4: `notify` never throws: not when the `PrintStream` throws a `RuntimeException`, not when `progress` or its
      fields are null.
- [ ] AC5: In the `cli` context there is exactly one `ProgressNotifier` bean, it is a `ConsoleProgressNotifier`, and
      there is no `SseProgressNotifier` bean and no `RunController` bean.
- [ ] AC6: In the default profile `SseProgressNotifier` is the only `ProgressNotifier` and `RunController` exists; all
      existing tests stay green.

## Test plan
- AC2–AC4: unit test `src/test/java/dev/mindforge/unit/cli/ConsoleProgressNotifierTest.java` (new), plain JUnit 5 +
  AssertJ, `make*` factory for `RunProgress` values, no Spring.
- AC5: extend `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java`.
- AC1, AC6: compile + existing integration suite (SSE tests, `ApiFlowTest`).

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`
- `src/main/java/dev/mindforge/api/controller/RunController.java`
- `src/main/java/dev/mindforge/cli/ConsoleProgressNotifier.java` (new)
- `src/main/java/dev/mindforge/cli/CliConfig.java` (new; `@Configuration @Profile("cli")`)
- `src/test/java/dev/mindforge/unit/cli/ConsoleProgressNotifierTest.java` (new)
- `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java`

## Out of scope
- A listener registry or fan-out of progress to several consumers (rejected option B).
- Changing `SseProgressNotifier` itself or `RunController`'s parameter type.
- Any command runner (P15-03+).

## Notes
