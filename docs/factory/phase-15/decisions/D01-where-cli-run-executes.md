---
id: D01
question: Q01
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q01.r1.tally.md]
adr:
---
## Decision
Use A: run the ingest in the CLI process, with no sweep. Put `@Profile("!cli")` on the `runWorkerTriggers` bean method
in `PipelineConfig` so that a `cli` profile has `RunWorker` but no trigger (no startup sweep, no periodic sweep, no
AFTER_COMMIT drain). The pipeline CLI runner calls `IngestionService.ingest(upload)`, then calls
`RunWorker.drain(kbId)` itself, waits until its own run is `COMPLETED` or `FAILED`, prints step progress, and calls
`RunWorker.stop()` before exit. Document in `deployment.md#one-live-instance` that the CLI is a live instance: stop
the server (or use a different database) before you run it.

## Why
- Plan 15.1 requires "same `IngestionService` and pipeline; prints step progress" (implementation-plan.md:1446-1448).
  Step progress is not persisted (scope.md:46), so only an in-process run can print it.
- The hazard is the sweep, not the worker: `RunWorker.sweep` fails every RUNNING run not in this JVM's `active` set,
  and `RunWorkerTriggers` runs it at startup and every PT1M (RunWorkerTriggers.java:34-42). Removing the whole
  triggers bean under `cli` means the pipeline CLI and the quiz CLI never touch another process's runs.
- `ingest` commits in `transactions.execute(...)` before it returns, so the explicit `drain` sees the QUEUED run and
  calls the model outside any transaction; the claim lease and fenced commits (ADR-0015) are reused unchanged.
- Not a contradiction of ADR-0015: the CLI is a short-lived instance that never sweeps, under a documented
  "stop the server first" rule — the same class as the deploy overlap deployment.md already accepts. No coordination
  logic is added (a heartbeat lease is out of scope, scope.md:22-23).
- Smallest change, fully reversible: remove the profile guard when a heartbeat lease exists.

## Rejected
- B (enqueue only): cannot print step progress; hangs with no live server.
- C (hybrid DB check): a heartbeat lease in all but name; out of scope.
- E (CLI keeps full `RunWorkerTriggers`): its startup sweep fails a live server's RUNNING runs; contradicts the one-live-instance ceiling.
- Split trigger class, AFTER_COMMIT only: more code than an explicit `drain` call, same effect; no ballot chose it.
- CLI calls `IngestPipeline.run` directly: duplicates RunWorker claim/fail logic in an adapter.

## Dissent
None (unanimous). Risk to watch: if an operator ignores the rule and a server is live, the server's sweep fails the
CLI's run as "interrupted" within one sweep interval and re-queues it (double model cost, no wiki corruption because
commits are fenced). The CLI must report this failure clearly.

## Implementation notes
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`: add `@Profile("!cli")` to the
  `runWorkerTriggers(RunWorker, Executor)` `@Bean` method. Keep `RunWorker` in all profiles; `@EnableScheduling` stays.
- `dev.mindforge.cli.PipelineCommand implements CommandLineRunner`, `@Profile("cli")`, wired as a bean in a
  `dev.mindforge.cli.CliConfig` `@Configuration` (constructor injection).
- Flow: `UUID docId = ingestionService.ingest(upload);` find the run with
  `IngestRunRepository.latestForDocument(kbId, docId)`. Dedup case: `ingest` returns an identical document and queues
  no run — if there is no run created by this call (no run, or the run is already terminal), print "already ingested"
  and exit 0 without waiting.
- Otherwise call `runWorker.drain(kbId)`. `drain` claims the OLDEST QUEUED run of the KB, which may not be the CLI's
  run; wait for the CLI's own run id, not the first claimed run. Poll `findById(kbId, runId)` (about 1 s) until
  COMPLETED/FAILED, with a bounded timeout property (e.g. `mindforge.cli.run-timeout`, default PT30M).
- Exit code: 0 on COMPLETED; non-zero on FAILED (print the failure reason) or on timeout.
- Call `runWorker.stop()` on exit (the `ContextClosedEvent` hook lived in `RunWorkerTriggers`, which is absent under
  `cli`). A further QUEUED run that `drain` claims just before exit is left RUNNING and the next server start re-queues
  it; document this.
- Step progress: a CLI `ProgressNotifier` prints `RunProgress(step, done, total)` for the CLI's run id (see Q05).
- Tests: (1) integration — start a `cli`-profile context with a RUNNING run row of "another process"; assert it is
  still RUNNING after the context is ready (no sweep). (2) unit — runner waits on its own run id when an older QUEUED
  run exists. (3) unit — dedup path exits 0 without waiting. (4) unit — FAILED run gives a non-zero exit code.
- `docs/project/deployment.md#one-live-instance`: add a CLI paragraph — the CLI is a live instance and runs no sweep;
  stop the server before `--spring.profiles.active=cli` on the same database, otherwise the server's sweep fails the
  CLI's run within one sweep interval and re-queues it.
