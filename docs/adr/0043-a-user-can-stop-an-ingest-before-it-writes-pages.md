# A user can stop a background run before it writes pages

Status: Accepted (2026-11-26).

An ingest run runs in the background for minutes on a slow local model. A document uploaded by mistake could not be
stopped, and the run kept spending model calls. `POST /api/v1/knowledge-bases/{kbId}/runs/{runId}/cancel` stops a
queued or running ingest or lint run. It answers 204 when the run is stopped and 409 `RUN_NOT_CANCELLABLE`
otherwise. Every page whose work is a model call also offers a Stop: a synchronous request (chat answer, flashcard or
quiz generation, quiz grading) is stopped in the browser, which abandons the request (see Consequences).

A stopped run ends as `FAILED` with the reason `cancelled by the user` and `retryable = false`. It writes nothing,
because the run stops before its first commit. The document then has a failed latest run, so the existing remove
action deletes it. No new run status and no migration are needed.

## Considered Options

- **A new `CANCELLED` status**: says more, but every status switch, the run summary and the frontend labels change, and
  the existing removal and retry rules would need new cases.
- **Cancel after the pages are written, by reverting them**: a revert is already offered for a completed run (ADR 0012).
  It is kept for mistakes found later. It does not stop the spending.

## Decision

- **Queued run**: a conditional update from `QUEUED` to `FAILED`, with no lease to release. It fails if the worker
  claimed the run first, and the caller gets 409.
- **Running run**: a fenced `RUNNING → FAILED` under the lease (the same fence as every other status change). The
  next fenced commit of the pipeline finds no matching row and throws `RunFencedException`, so nothing is written.
- **Checkpoints**: the pipeline and the lint review re-read the run's status before each model call. A stopped run
  stops before its next call, not at its next commit. `RunFencedException` passes through the per-chunk catches of
  lint, so a stop is never recorded as a finding.
- **Interrupt**: the worker interrupts the run's thread. A model call that waits in the gateway returns at once.
- **Not stoppable**: a run that already writes pages (`WRITTEN`) and a revert. These answer 409.

## Consequences

- A synchronous request that the browser abandons still runs to its end on the server, within its deadline (10 s
  interactive, 60 s batch). Its result is discarded; a flashcard or quiz it wrote stays in the study data.
- A model call already sent to the server runs to its end on the server. The client cannot cancel an HTTP request
  in flight, so a local model keeps generating until that call ends.
- A stopped run is not retried. Its interrupted model call is recorded as a transient failure, but the run is already
  `FAILED` and `retryable = false`, so the worker does not re-queue it.
- The stop and the commit race is decided by the fence alone: a stop that lands after commit 1 is refused with 409, and
  the run completes. The user then reverts it.
- A model timeout during a page draft now fails the whole attempt, which the worker retries. Before this change such a
  timeout only failed that one page.
