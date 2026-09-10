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
