---
id: T17
title: The ingest run lifecycle — claiming, queueing, fencing, failing
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — the lease and queue in T07 have holes that lose work or leave it
stuck.

**1. Run row vs. lease claim ordering.** `knowledge_bases.active_run_id` is an FK to `ingest_runs`
(`docs/wayfinder/tickets/07-idempotency-and-failure.md:87`), so the run row must exist before the claim, and
`IngestRunRepository` lists `create` and `claimLease` separately (`docs/project/implementation-plan.md:570`). Insert the
row, lose the claim, and a `RUNNING` row is left behind. The document now "has a run", so it stops being pending
(`tickets/07-…:179`), is never drained, and the next sweep marks it FAILED.

**2. The queue only holds first uploads.** "A document with no run is pending" has no representation for:
- a retry of a failed document (`tickets/07-…:101`);
- a revert;
- a full Lint.

If any of these arrives while the lease is held, it is lost. The drain picks only "the oldest pending document".

**3. No retry endpoint.** Phase 9.7 (`implementation-plan.md:791`) has none, although T07 makes retry the only recovery
from a failed run.

**4. A stale worker can still commit.** Commits never check that the run still holds the lease. Railway and Render keep
the old instance up until the new one is healthy. The new instance's startup sweep fails the old instance's `RUNNING`
run and frees the lease while the old one is still generating, so both commit — the lost update the lease exists to
prevent. Phase 13.3's "single instance" (`implementation-plan.md:1033`) does not hold during a deploy.

**5. Failures that are not JVM crashes.**
- A Supersede exception after commit 1 falls under "Failure: run FAILED" (`implementation-plan.md:667`). Pages already
  landed, so the run should be COMPLETED with `supersession_skipped`.
- Lease release is not specified as a `finally`, so an in-process exception holds the lease until restart.

**6. Missed and interrupted work.**
- A lost `DocumentIngested` wake-up leaves an upload stuck until the next restart.
- The sweep marks interrupted runs FAILED, and their documents are not pending. Every deploy therefore fails in-flight
  uploads, and each needs a manual retry.

**7. Burst at startup.** The sweep drains every knowledge base at once. The write semaphore is described per run
(`docs/wayfinder/tickets/04-ingest-execution-model.md:198`), and there is one shared circuit breaker (ADR 0009). A burst
can open the breaker and fail every run.

Decide:

- **Claim atomicity.** *Recommended:* insert the run and run the conditional update in one transaction; roll back on 0
  rows.
- **Non-document work while busy.** *Recommended:* 409 "knowledge base busy" for revert, full Lint and retry.
  *Alternative:* a `QUEUED` status plus a drain that reads runs, not only documents.
- **Retry endpoint.** E.g. `POST /api/documents/{id}/runs`.
- **Fencing.** *Recommended:* both commits update the run `WHERE run_id = :run AND status = :expected` and check
  `active_run_id = :run`; on 0 rows, abort and log.
- **Per-phase failure table.**
  - Before commit 1 → FAILED.
  - Between commit 1 and commit 2 → COMPLETED + `supersession_skipped`.
  - The lease is released in a `finally`.
- **Periodic drain.** *Recommended:* an `@Scheduled` job calling the same drain as the startup sweep.
- **Interrupted runs.** Re-queue on sweep, i.e. treat a latest run FAILED as "interrupted" as pending — or keep them
  FAILED and retryable.
- **Throttling.** One global write semaphore across all runs.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. One recommendation is overridden — the
queue — and the reason is given in decision 1.

**Summary.**

- Every run is created `QUEUED`. The queue is made of runs, not documents.
- A claim moves a run from `QUEUED` to `RUNNING` and takes the lease, in one transaction.
- Each commit is fenced on the run's status.
- The terminal transaction always releases the lease.
- One sweep, run at startup and every minute, settles anything this process is not running, re-queues interrupted
  ingests and drains the queues.
- Every background model call goes through one global semaphore.

```
QUEUED ──claim──▶ RUNNING ──commit 1──▶ WRITTEN ──commit 2──▶ COMPLETED
                     │                     │
                     └──────▶ FAILED       └── Supersede fails ──▶ COMPLETED + supersession_skipped
REVERT runs: inserted and COMPLETED in one transaction (T16) — never queued
```

### Decisions

**1. The queue is `QUEUED` runs.** *This overrides the recommended 409.* The rule "a document with no run is pending"
covers first uploads only. A `QUEUED` status covers every source of work with one rule:

- **Upload** — inserts its `Document` and a `QUEUED` `INGEST` run in the same transaction (T24's dedup, then the run).
- **Retry** — inserts a `QUEUED` `INGEST` run for an existing document.
- **Conversation edit** — is an upload (T15).
- **Full Lint** — inserts a `QUEUED` `LINT` run.

The drain takes the oldest `QUEUED` run of a knowledge base by `created_at`, whatever its kind. It sends `INGEST` runs to
`IngestPipeline` and `LINT` runs to `LintService`. Nothing is lost while the lease is held.

A 409 for retry and Lint would make the learner wait out a five-minute ingest and click again. It would also leave
interrupted runs (decision 6) with nowhere to go.

**Reverts still return 409 when busy** (T16). A revert is one quick transaction, and a user who clicks "undo" wants it to
happen now or be told why not.

**2. Claim atomicity.** The run already exists as `QUEUED`, so no orphan `RUNNING` row can appear. The claim is one
transaction:

```sql
UPDATE knowledge_bases SET active_run_id = :run WHERE kb_id = :kb AND active_run_id IS NULL;   -- 0 rows → rollback; run stays QUEUED
UPDATE ingest_runs SET status = 'RUNNING', started_at = now()
 WHERE run_id = :run AND knowledge_base_id = :kb AND status = 'QUEUED';                         -- 0 rows → rollback
```

The two paths cannot deadlock:

- **The claim** locks the knowledge-base row, then a `QUEUED` run.
- **Commits and the sweep** lock a `RUNNING` or `WRITTEN` run, then the knowledge-base row.

Neither ever waits on the other's run row.

**3. Endpoints.**

| Endpoint | Action | Responses |
|---|---|---|
| `POST /api/documents/{id}/runs` | Retry | 202 with the run id. Allowed only when the document's latest run is `FAILED`; 409 otherwise, including while it has a `QUEUED` or active run. The SPA shows the button only when the run is `retryable`. |
| `POST /api/knowledge-bases/{kbId}/lint-runs` | Full Lint | 202. 409 if a `LINT` run is already `QUEUED` or active in that knowledge base. |
| `POST /api/runs/{id}/revert` | Revert | Synchronous. 200; 409 when busy; 409 when revert is not offered. |
| `DELETE /api/knowledge-bases/{kbId}/supersessions/{id}` | Remove one supersession | Synchronous. 200; 409 when busy; 409 when not offered. |

**4. Fencing.** Commit 1 opens with:

```sql
UPDATE ingest_runs SET status = 'WRITTEN' WHERE run_id = :run AND status = 'RUNNING'
   AND EXISTS (SELECT 1 FROM knowledge_bases WHERE kb_id = :kb AND active_run_id = :run)
```

Commit 2 opens with the same statement, moving `WRITTEN` → `COMPLETED`. The terminal `FAILED` update is fenced the same
way.

If the update matches 0 rows, the transaction rolls back and throws `RunFencedException`. The worker logs a WARN and
stops. It writes nothing, releases nothing and notifies nothing, because the lease now belongs to someone else. This is
what makes the sweep safe while a deploy's old instance is still generating.

**5. Failure by phase.**

| Where it fails | Run becomes | Lease |
|---|---|---|
| Any step before commit 1, or commit 1 itself | `FAILED`, with `failure_reason` and `retryable` | released in that same transaction |
| No page task succeeded (T21) | `FAILED` | released |
| Link check | degrades; recorded in `failures` (unchanged, T10) | — |
| Supersede generation, its checks, or commit 2 | `COMPLETED`, `supersession_skipped = true`, reason in `failures` | released |
| Fenced | untouched | untouched |

The terminal transaction runs from a `finally`. If that transaction itself fails — say the database is down — the run
stays `RUNNING` or `WRITTEN` in the database but is no longer running in this process. The next sweep settles it.

`retryable` becomes a column, `ingest_runs.retryable BOOLEAN NULL`, set when a run goes `FAILED`:

- **false** for a RelevanceGuard rejection, an edit that named no page, or the page-task cap (T23);
- **true** for everything else.

**6. One sweep, at startup and every 60 seconds** (`ApplicationReadyEvent` plus `@Scheduled(fixedDelay)`).

The worker keeps an in-memory set of the run ids it is executing. A run id joins the set *before* its claim transaction
and leaves it after the terminal transaction, so the sweep can never see a claimed run that is missing from the set.

The sweep does three things, in order:

1. **`WRITTEN` runs not in the set** → `COMPLETED` + `supersession_skipped`; release the lease.
2. **`RUNNING` runs not in the set** → `FAILED`, `failure_reason = "interrupted"`, `retryable = true`; release the lease.
   - An interrupted `INGEST` run whose `attempt < 3` is **re-queued** as a new `QUEUED` run for the same document with
     `attempt + 1`.
   - An interrupted `LINT` run is not re-queued, since the learner asked for it once.
3. **Drain** every knowledge base that has `QUEUED` runs.

This covers a lost wake-up event, a failed `finally` and a deploy. A deploy no longer fails in-flight uploads: they re-run
once, automatically.

`ingest_runs.attempt SMALLINT NOT NULL DEFAULT 1` caps the loop. A document that kills the JVM every time fails for good
after three tries, rather than on every restart. An explicit retry starts again at attempt 1.

**Ceiling, restated.** This assumes one *live* instance.

- During a deploy's overlap, each instance's sweep treats the other's runs as abandoned. Fencing keeps that correct; the
  cost is re-generating whatever was in flight.
- Running two instances permanently needs a heartbeat lease (`lease_renewed_at`) instead of the in-memory set.
- On shutdown (`ContextClosedEvent`), the worker stops claiming.

**7. Wake-ups.**

- Inserting a `QUEUED` run publishes `IngestRunQueued(runId, knowledgeBaseId)` in the same transaction. An
  `AFTER_COMMIT` listener calls `drain(kbId)` (T20).
- A run that finishes calls `drain(kbId)` directly.
- A missed event waits at most one sweep interval.

**8. Throttling.** One global `Semaphore` (`mindforge.ai.background-permits`, default 4) wraps every model call made by an
`INGEST` or `LINT` run, across all knowledge bases. It replaces T04's per-run write semaphore.

A post-deploy drain of many knowledge bases therefore queues its calls instead of opening the shared circuit breaker
(ADR 0009). Query and study calls do not take a permit.

### Feeds

- **T20** — `IngestRunQueued` is the only run domain event.
- **T24** — deleting a knowledge base returns 409 while `active_run_id` is set; `QUEUED` runs go with the cascade.
- **T25** — `IngestRunRepository`'s methods follow this lifecycle.
- **T29** — amend ADR 0015 (queue, claim, fencing, sweep); update 5.2/5.3, 6.8/6.9, 9.6/9.7 and 13.3, and the
  architecture idempotency section. `ingest_runs` gains `QUEUED`, `attempt`, `retryable` and `created_at`.
