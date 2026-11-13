---
id: P15-06
title: Run the pipeline command's Ingest Run in process and exit with its outcome
status: ready
plan_tasks: [15.1]
decisions: [D01, D08]
depends_on: [P15-05]
size: L
issue:
commit:
fix_rounds: 0
---
## Goal
After `ingest` returns, the pipeline command drains the Knowledge Base's queue in its own process, prints step progress
of its own Ingest Run, waits until that run is COMPLETED or FAILED (bounded by `mindforge.cli.run-timeout`), stops the
worker and exits 0 / 1 / 2 per the D08 outcome table. `--lesson-id` and `--new-version` map 1:1 onto `DocumentUpload`.
An end-to-end `cli`-profile test ingests a Markdown file to COMPLETED.

## Context
- After P15-05: `dev.mindforge.cli.PipelineCommandRunner` parses `--file --kb --user`, resolves the user, checks
  ownership, builds `DocumentUpload(..., UploadSource.CLI, ..., null, false)`, calls `IngestionService.ingest`, maps
  refusals to exit 2, and (interim) prints the document id and exits 0. Replace the interim end.
- After P15-01/P15-02: under `cli` there is no `RunWorkerTriggers` (no sweep, no AFTER_COMMIT drain), `RunWorker`
  exists, and the `ProgressNotifier` bean is `ConsoleProgressNotifier` with `watch(UUID runId)`.
- `application/service/RunWorker.java`: `drain(UUID kbId)` claims the OLDEST QUEUED run of the KB (maybe not the CLI's)
  and executes it on `runExecutor`; when a run ends it calls `drain(kbId)` again; `stop()` stops further claims.
- `domain/port/IngestRunRepository.java`: `latestForDocument(kbId, documentId)`, `findById(kbId, runId)`.
  `domain/model/IngestRun.java`: `status`, `failureReason`, `failures` (`List<RunFailure>`), `supersessionSkipped`.
- `IngestionService.ingest` commits the document and QUEUED run before it returns. For identical content it returns the
  existing document id and queues no run, even when every run of that document FAILED
  (`IngestionService.java` ~90-92, ~166-168).
- `infrastructure/config/AppProperties.java` (`@ConfigurationProperties(prefix = "mindforge")`, nested static classes).
- `docs/project/deployment.md#one-live-instance` (one live instance; the server sweep fails a run not in its own
  process). ADR-0015 (claim lease, fenced commits).
- Integration tests: `TestContainerBase`, `StubAiConfig`/`StubAIGateway`, `TestFixtures`; see
  `integration/application/IngestRunTest.java` for how a stubbed ingest reaches COMPLETED.

## Decisions to apply
**D01 — Decision:** Run the ingest in the CLI process, with no sweep. The pipeline CLI runner calls
`IngestionService.ingest(upload)`, then calls `RunWorker.drain(kbId)` itself, waits until its own run is `COMPLETED` or
`FAILED`, prints step progress, and calls `RunWorker.stop()` before exit.

**D01 — Implementation notes:**
- Flow: `UUID docId = ingestionService.ingest(upload);` find the run with `IngestRunRepository.latestForDocument(kbId,
  docId)`. Dedup case: `ingest` returns an identical document and queues no run — if there is no run created by this
  call (no run, or the run is already terminal), do not wait (outcome per D08 below).
- Otherwise call `runWorker.drain(kbId)`. `drain` may claim an older QUEUED run first; wait for the CLI's own run id.
  Poll `findById(kbId, runId)` (about 1 s) until COMPLETED/FAILED, with a bounded timeout property
  `mindforge.cli.run-timeout`, default PT30M.
- Call `runWorker.stop()` on exit (the `ContextClosedEvent` hook lived in `RunWorkerTriggers`, absent under `cli`). A
  further QUEUED run that `drain` claims just before exit is left RUNNING and the next server start re-queues it.
- Step progress: call `ConsoleProgressNotifier.watch(runId)` before `drain` so only the CLI's run is printed.
- If a live server's sweep fails the CLI's run as "interrupted", the CLI must report this failure clearly (exit 1 with
  the reason).
- Tests: unit — runner waits on its own run id when an older QUEUED run exists; dedup path exits without waiting;
  FAILED run gives a non-zero exit code.

**D08 — Decision:** The pipeline CLI takes `--file`, `--kb`, `--user`, an optional `--lesson-id <id>` and a boolean
`--new-version` flag, mapped 1:1 onto `DocumentUpload.lessonId` / `DocumentUpload.newVersion` with `UploadSource.CLI`.
It waits for its own run as D01 says and ends with **0** = success, **1** = the ingest ran (or ran before) and failed,
**2** = the input was refused before any run was queued.

**D08 — Implementation notes:**
- `--lesson-id` single value; blank = absent. `--new-version` is a flag (`ApplicationArguments.containsOption`). No
  CLI-side rule about `--new-version` without `--lesson-id`: pass through and let `IngestionService` decide.
- Exit goes through `SpringApplication.exit` (already in place via P15-03's `CliExitOnReady`). Never `System.exit` in
  the runner.
- Outcome table:
  - new run COMPLETED -> 0; if it has run failures (`failures` not empty) or `supersessionSkipped`, print one warning
    line to stderr.
  - new run FAILED -> 1, print the failure reason. Wait timeout -> 1.
  - identical upload (no run created by this call): print document id and latest run state, do not wait; latest run
    COMPLETED / QUEUED / RUNNING -> 0; latest run FAILED -> 1 with a hint that the document must be retried or removed
    in the web UI.
  - `LessonAlreadyExistsException` -> 2, message names `--new-version` or another `--lesson-id`.
  - `UnknownLessonException`, `LessonIdentityException`, `UploadRejectedException` -> 2, exception message on stderr.
  - usage error, missing/unknown `--user`, unknown KB, `NotOwnerException`, unreadable `--file` -> 2.
  - any other unexpected exception -> 1 (logged).

## Ticket-writer decision (which run is "created by this call")
`ingest` does not say whether it deduplicated, and `IngestRun.createdAt` comes from the database clock. Use D01's
operational rule: after `ingest`, read `latestForDocument(kbId, docId)`:
QUEUED -> treat as this call's run (watch, drain, wait); RUNNING -> held by another process: print state, exit 0 without
waiting (D08); COMPLETED -> print "already ingested", exit 0; FAILED -> exit 1 with the retry/remove hint; no run -> exit 0.
Record this in the ticket Notes if you deviate.

## Acceptance criteria
- [ ] AC1: `--lesson-id x --new-version` reaches `ingest` as `DocumentUpload(..., "x", true)`; no flags -> `(null,
      false)`; a blank `--lesson-id` is `null`.
- [ ] AC2: `LessonAlreadyExistsException` -> exit 2 and the stderr message mentions `--new-version`; no drain.
- [ ] AC3: Latest run QUEUED -> the runner calls `watch(runId)`, then `drain(kbId)`, polls `findById(kbId, runId)` for
      its own run id (not the first claimed run) and exits 0 when it is COMPLETED.
- [ ] AC4: COMPLETED with non-empty `failures` or `supersessionSkipped` -> exit 0 and one warning line on stderr.
- [ ] AC5: FAILED -> exit 1 and the failure reason on stderr.
- [ ] AC6: No terminal status within `mindforge.cli.run-timeout` -> exit 1 with a timeout message.
- [ ] AC7: Identical upload with latest run COMPLETED -> exit 0, `drain` never called; with latest FAILED -> exit 1 with
      the retry/remove hint, `drain` never called; with latest RUNNING -> exit 0, `drain` never called.
- [ ] AC8: `runWorker.stop()` is called on every path that called `drain`.
- [ ] AC9: `mindforge.cli.run-timeout` is bound in `AppProperties` with default PT30M.
- [ ] AC10: End to end: in a `cli` context (Testcontainers PostgreSQL, `StubAIGateway`,
      `mindforge.cli.exit-when-done=false`), running the `PipelineCommandRunner` bean with
      `pipeline --file <temp .md> --kb <id> --user <owner email>` ends with exit code 0, the document has
      `UploadSource.CLI` and its run is COMPLETED, and stdout contains at least one progress line for that run.
- [ ] AC11: Runner tests in AC3–AC8 run without a Spring context and without real sleeps longer than the test needs
      (inject the poll interval/clock or a small timeout).

## Test plan
- AC1–AC9, AC11: unit, `src/test/java/dev/mindforge/unit/cli/PipelineCommandRunnerTest.java` (Mockito
  `IngestRunRepository`, `RunWorker` (mock), `ConsoleProgressNotifier` (mock or real with a buffer); `make*` factory for
  `IngestRun` states; `DefaultApplicationArguments`; `@TempDir`).
- AC10: integration, `src/test/java/dev/mindforge/integration/cli/PipelineCommandIntegrationTest.java` (new),
  `@Tag("integration")`, `@ActiveProfiles("cli")`, extends `TestContainerBase`; call `run(new
  DefaultApplicationArguments(...))` on the bean with a captured stdout, or construct a runner from context beans.

## Files likely touched
- `src/main/java/dev/mindforge/cli/PipelineCommandRunner.java`
- `src/main/java/dev/mindforge/cli/CliConfig.java`
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java` (nested `Cli` with `runTimeout`)
- `src/main/resources/application-cli.yml` (optional explicit `mindforge.cli.run-timeout`)
- `src/test/java/dev/mindforge/unit/cli/PipelineCommandRunnerTest.java`
- `src/test/java/dev/mindforge/integration/cli/PipelineCommandIntegrationTest.java` (new)

## Out of scope
- Calling `IngestPipeline.run` directly or copying `RunWorker` claim/fail logic into the adapter.
- A heartbeat lease, a database check for a live server, or re-adding a sweep under `cli`.
- A CLI-side rule for `--new-version` without `--lesson-id`; renumbering exit codes.
- Documentation (P15-08).

## Notes
