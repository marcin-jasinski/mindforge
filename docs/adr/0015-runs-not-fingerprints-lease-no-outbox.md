# Re-runs are new runs, a lease serializes writers, and there is no outbox table

Step fingerprinting is removed. Ingest mutates a wiki that other documents have already changed,
so a re-run *should* produce different output, and no step's inputs are ever unchanged. The
`ingest_runs` row is the single record of a run: its status (`RUNNING | WRITTEN | COMPLETED | FAILED`),
per-page failures, each service's `VERSION` and model id, and cost. Pages written are counted from
the revision rows the transaction inserted, never from a step's own report.

- **Idempotency moves to the edges.** Identical bytes into the same knowledge base are rejected by
  `UNIQUE (knowledge_base_id, content_hash)`. A revised upload is a new `Document` of the same
  lesson and a new run; there is no retraction. A startup sweep settles interrupted runs.
- **One page-writing run per knowledge base**, because Resolve reads wiki state minutes before
  commit. The mechanism is a lease column claimed with a conditional update
  (`… SET active_run_id = :run WHERE active_run_id IS NULL`), held across generation and released
  in the run's final transaction. Documents without a run are the queue.
- **No outbox.** A run's commit and its domain events are published in the same transaction;
  listeners run after commit and must tolerate missing an event, because every consumer (SSE
  progress, caches) is derived or best-effort.

## Considered Options

- **In-process lock**: invisible to the database and the SPA, and wrong on a second instance.
- **Session advisory lock**: pins a pooled connection for minutes.
- **Transactional outbox with relay**: at-least-once delivery buys nothing when a missed event is
  repaired by rebuilding from Postgres.

## Consequences

- The sweep treats every `RUNNING` run as abandoned, which assumes one instance; multiple
  instances need a lease expiry.
- `StepCheckpoint`, `StepFingerprint` and `DocumentStatus` are deleted.

Decided in [T07](../wayfinder/tickets/07-idempotency-and-failure.md).

## Amendments

2026-09-12, from the spec review ([T17](../wayfinder/tickets/17-run-lifecycle.md),
[T20](../wayfinder/tickets/20-progress-and-domain-events.md), [T24](../wayfinder/tickets/24-persistence-mechanics.md)):

- **The queue is runs, not documents.** Every run is inserted `QUEUED` — uploads, conversation edits, retries, full
  Lints — and the worker claims the oldest per knowledge base: the lease and `QUEUED` → `RUNNING` in one transaction, so
  a lost claim leaves nothing behind. Reverts are synchronous and return 409 while a run is active.
- **Commits are fenced.** Each commit updates the run only from its expected status while it holds the lease; 0 rows
  aborts without writing.
- **The startup sweep becomes one sweep**, at startup and every minute, over runs not executing in this process:
  `WRITTEN` completes with `supersession_skipped`; `RUNNING` fails as interrupted, and an ingest is re-queued, up to three
  attempts. A Supersede failure completes the run rather than failing it; the terminal transaction runs from a `finally`.
- **One global permit pool** throttles every background model call across knowledge bases.
- **Progress is not a domain event.** A best-effort `ProgressNotifier` port streams it per knowledge base; the only run
  domain event is `IngestRunQueued`, which wakes the worker after commit. `DomainEvent` no longer requires a document id.
- **Dedup is checked under the knowledge-base row lock** that also serializes the lesson rule; the unique constraint is
  the backstop.
- Ceiling restated: one *live* instance. During a deploy's overlap fencing keeps runs correct; permanently multiple
  instances need a heartbeat lease.
