---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W01, W03, W04]
---
## Question
Where does an ingest run started by the pipeline CLI execute, and does a CLI process run the run worker and its sweep
at all — given that a live server may share the same database?

## Context
- Plan 15.1: "ingests through the same `IngestionService` and pipeline; prints step progress."
- `IngestionService.ingest` only queues the run. `RunWorker.drain` executes it, woken by `RunWorkerTriggers`
  (AFTER_COMMIT of `IngestRunQueued`, `ApplicationReadyEvent` sweep, periodic sweep every PT1M).
- `RunWorker.sweep` fails every `RUNNING` run not in this process's in-memory `active` set, and re-queues ingests.
  So with a server and a CLI on one database:
  - the CLI's startup sweep fails the server's in-flight runs as "interrupted";
  - the server's periodic sweep fails the CLI's in-flight run within a minute and re-queues it (then whichever
    process drains first regenerates it).
- This also applies to the quiz CLI: if the `cli` profile keeps `RunWorkerTriggers`, merely starting a quiz sweeps the
  server's runs.
- `docs/project/deployment.md#one-live-instance` and ADR-0015 (Consequences; Amendment 2026-09-12) set a one-live-instance
  ceiling; a heartbeat lease is the stated fix for more than one, and is not in this phase.

## Known options
- A: In-process run. The CLI process keeps `RunWorker`; it ingests, its AFTER_COMMIT trigger drains the run in-process,
  and the CLI waits for the terminal status. Document that the CLI must not run while a server is live on the same
  database (it is a second instance). No sweep in the CLI profile, or only the AFTER_COMMIT trigger.
- B: Enqueue only. The CLI calls `IngestionService.ingest` and does not run the worker (no `RunWorkerTriggers` in the
  `cli` profile); the live server's periodic sweep (≤ 1 min) drains the queued run. The CLI polls `ingest_runs` for
  status. Step progress (not persisted) cannot be printed — only status changes. Needs a live server.
- C: Hybrid: in-process run (A) only when no other process holds work, detected via a DB check — needs new
  coordination logic (close to a heartbeat lease, out of scope).

## Constraints
- One live instance (deployment.md; ADR-0015). A choice that silently makes the CLI a second permanent worker
  contradicts the accepted ADR and is a blocker.
- "Prints step progress" (plan 15.1) — option B cannot meet it without persisting steps.
- Never call a model inside a database transaction; commits stay fenced (CLAUDE.md, ADR-0015).
