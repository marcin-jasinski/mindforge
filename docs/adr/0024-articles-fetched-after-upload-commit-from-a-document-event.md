# Articles are fetched after the upload commits, from a document event

Status: Accepted (2026-11-14).

When `fetchExternalArticles` is true, an upload's linked articles are fetched after the upload transaction commits.
They are not fetched in the request, in the ingest run, or after the run completes. `IngestionService.ingest`
publishes a new `DomainEvent.DocumentAccepted` in the upload transaction, and only when it inserts a new document. An
`AFTER_COMMIT` listener gives the work to the virtual-thread run executor. An application service then extracts the
URLs, fetches them with no transaction open, and queues each article through `IngestionService.ingest` as an
`ARTICLE` upload. So each article has its own document, `QUEUED` run and `IngestRunQueued` in one transaction.

`DocumentAccepted` is a document event, not a run event. ADR 0015's statement that "the only run domain event is
`IngestRunQueued`" still holds. Its consumer is best-effort, and this is how ADR 0015 lets a listener miss an event.
The ingest pipeline of ADR 0013 does not change.

## Considered Options

- **Fetch synchronously in the upload request after commit**: the 202 upload waits for N third-party timeouts, and a
  fetch failure can surface as an error on an upload that has already committed.
- **Fetch in `IngestionService` on its own executor, with no event**: correct and smaller. But it puts article policy
  into upload acceptance, which is different from how the codebase reacts to a commit.
- **Reuse `IngestRunQueued`, filtered to the first run**: it fires again on every retry and sweep re-queue, and it
  repeats outbound HTTP.
- **Fetch after the parent's run completes**: needs a run-completed hook in the worker and in the sweep. It gives no
  ordering gain, because the parent's run is queued first and the worker claims the oldest run.
- **Fetch as a pipeline step**: it breaks the closed pipeline (ADR 0013) and holds the knowledge-base lease during HTTP.
- **A durable "articles pending" column drained by the sweep**: no lost fetch, but it needs a migration and a new queue
  concept for an off-by-default feature.

## Consequences

- The upload's latency and status do not depend on third-party hosts.
- A crash, a shutdown or a rejected executor task after commit loses that upload's articles, and nothing repairs this.
  This is acceptable only because the feature is off by default and best-effort. If loss stops being acceptable, a
  durable marker drained by the sweep replaces the event.
- `retry` and the sweep never fetch again. A fetch is a one-time effect of a newly inserted document.
- The parent's run is ingested before its articles, because runs queue oldest first per knowledge base.
- `ARTICLE` and `CONVERSATION` documents do not trigger a fetch (no recursion).
- A CLI process can exit before a background fetch completes. The deployment notes record which behavior the `cli`
  profile has.

Decided in [D01](../factory/phase-17/decisions/D01-when-and-where-articles-are-fetched.md) by the dark-factory run
`phase-17`.
