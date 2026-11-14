---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W04, W05, W07]
---
## Question
At which point, and on which thread, does `ArticleFetcher` run for an uploaded document: in the upload request, in an
after-commit listener, or after the document's own ingest run?

## Context
- Plan 17.2: "Extracts URLs from a document's blocks … Each article becomes a `Document` … with a `QUEUED` run like
  any upload." It does not say when.
- `IngestionService.ingest` parses outside its transaction, then inserts Document + QUEUED run + `IngestRunQueued` in
  one transaction (`src/main/java/dev/mindforge/application/service/IngestionService.java`). HTTP 202 is returned after
  that.
- `DomainEvent` has only `IngestRunQueued`; `RunWorkerTriggers` drains on it `AFTER_COMMIT`. No outbox: a listener
  must tolerate a missed event (ADR 0015).
- `IngestPipeline` is closed (ADR 0013); a fetch step inside a run is a design change and would hold the
  knowledge-base lease during network I/O.
- Fetching N URLs with timeouts can take tens of seconds. A retry of a failed run (`IngestionService.retry`) and the
  sweep re-queue a run; whether they fetch again depends on the choice.
- Order matters for the wiki: an article queued before its parent's run completes is ingested after the parent (runs
  queue oldest first per knowledge base), unless queued earlier.

## Known options
- A: Synchronous in `IngestionService.ingest`, after the upload transaction commits and only for a newly inserted
  document: fetch, then ingest each article in its own transaction. Simple; the upload response waits for the fetch
  (virtual threads make blocking fine). A crash between the commits loses the articles.
- B: New domain event (e.g. `DocumentAccepted`) published in the upload transaction; an `AFTER_COMMIT` listener runs
  the fetch on a virtual thread. Upload returns at once; a missed event means the articles are never fetched (no
  outbox; acceptable per ADR 0015 "tolerate missing an event").
- C: After the parent document's INGEST run completes (hook in `RunWorker` or a run-completed event). Articles land
  after the parent's pages, so "create-only" never blocks the parent's own Concepts; adds a run-completed event or a
  `RunWorker` dependency; a failed parent fetches nothing.
- D: Inside `IngestPipeline` as a step. Holds the lease during HTTP; contradicts the closed pipeline unless ADR 0013 is
  amended. Weak.

## Constraints
- No network I/O inside a database transaction; each article's Document + run + event in one transaction.
- Listeners run after commit and tolerate a missed event; no outbox (CLAUDE.md; ADR 0015).
- A new pipeline step is a design change needing an ADR 0013 amendment (CLAUDE.md; ADR 0013).
- Off by default: with the setting false, nothing is extracted or fetched.
