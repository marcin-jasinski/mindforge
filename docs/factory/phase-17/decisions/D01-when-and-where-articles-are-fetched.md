---
id: D01
question: Q01
status: decided
method: majority
class: adr
votes: { pragmatist: E, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q01.r1.tally.md]
adr: docs/adr/0024-articles-fetched-after-upload-commit-from-a-document-event.md
---
## Decision
Use B. Add `DomainEvent.DocumentAccepted` and publish it in the upload transaction of `IngestionService.ingest`, only
on the branch that inserts a new document. An `AFTER_COMMIT` listener in infrastructure hands the work to the
virtual-thread `runExecutor`. There, an application service extracts the URLs and fetches them with no transaction
open. It then queues each article through `IngestionService.ingest` as an `ARTICLE` upload, so each article has its
own transaction. The upload request does not wait for the fetch. Retry and sweep never fetch again. With
`fetchExternalArticles` false, nothing is extracted or fetched.

## Why
- The upload contract is asynchronous (`DocumentController.java:53` returns 202). A puts N third-party timeouts on
  that request, and a fetch exception after commit can turn a committed upload into an error response.
- B uses the existing pattern: an event in the same transaction and an `AFTER_COMMIT` listener that calls
  `executor.execute` (`RunWorkerTriggers.java:29-32`; `hexagonal.md` §Domain Events).
- ADR 0015 accepts a lost event for best-effort consumers (0015:17-18). This consumer is off by default and best-effort.
  A and E have the same crash window, so B loses nothing that they keep.
- Ordering does not need C. The parent's run is queued in the upload transaction, and the worker claims the oldest
  run per knowledge base (0015:41). So the article runs come after the parent's run.
- The event fires only on insert (`IngestionService.java:107-110`). `retry` and `enqueue` do not publish it, so a
  re-queue never fetches again.
- ADR 0015 says "the only run domain event is `IngestRunQueued`". `DocumentAccepted` is a document event, not a
  run event. ADR 0024 records this extension, so nothing contradicts the accepted ADR.

## Rejected
- A: the 202 upload waits for tens of seconds of third-party I/O, and a fetch failure can surface on a committed upload.
- E: correct, and it was a close second. But it puts article policy into upload acceptance and is less consistent with the event pattern.
- B': re-fires on every retry and sweep re-queue, and causes repeated outbound HTTP. It also needs a "first run" query.
- C: needs a run-completed hook in `RunWorker` and in the sweep's WRITTEN→COMPLETED path. It also needs an ADR 0015 amendment and gives no ordering gain.
- D: verified hard objection. It breaks the closed pipeline (ADR 0013) and holds the lease during HTTP (ADR 0015).
- F: durable "pending" column. It needs a migration and a new queue concept for an off-by-default feature (minimal-implementation.md).

## Dissent
Pragmatist (E): the same trigger point without a new event type is smaller. Two risks to monitor. First, a crash, a
shutdown or a rejected executor task loses the articles, and nothing repairs this. Second, in the `cli` profile the
process exits after the parent run, which can cut off a background fetch.

## Implementation notes
- `domain/model/DomainEvent.java`: `sealed interface DomainEvent permits IngestRunQueued, DocumentAccepted`;
  `record DocumentAccepted(UUID documentId, UUID knowledgeBaseId, UUID uploadedBy, UploadSource source, Instant occurredAt)`.
- `IngestionService.ingest`: publish `DocumentAccepted` inside the transaction, after `documents.insert`, only on the
  insert branch. Do not publish it for an identical document, in `submitEdit`, in `retry` or in `enqueue`.
- Application service (name and port per Q09, e.g. `ArticleIngestion.fetchFor(UUID kbId, UUID documentId, UUID userId)`):
  return immediately when `!settings.fetchExternalArticles()` or the source is `ARTICLE` or `CONVERSATION` (no recursion).
  Load the blocks with `DocumentRepository.findById(kbId, documentId)`. Fetch every URL outside a transaction. For each
  article, call `ingestion.ingest(new DocumentUpload(kbId, userId, UploadSource.ARTICLE, ...))`. Catch
  `LessonAlreadyExistsException` (log and skip, T18), `UploadRejectedException`, `LessonIdentityException` and per-URL
  fetch failures, log each with fixed text, and continue. One bad URL must not stop the others.
- `infrastructure/event/ArticleFetchTriggers`: `@TransactionalEventListener(phase = AFTER_COMMIT) onDocumentAccepted(e)`
  calls `executor.execute(() -> articles.fetchFor(...))`. Wrap the whole task in a catch-all that logs, so no failure
  escapes the executor silently. Wire the bean in a `@Configuration` with `runExecutor`. Make an explicit, documented
  choice about the `cli` profile: either register the bean there too and accept that the fetch can be cut off, or
  leave it out. In both cases, record the choice in deployment.md.
- Bound the total time: put a URL-count cap per document and a per-URL timeout in `AppProperties`.
- Tests: `IngestionServiceTest` checks that the event is published only for a new document, and not for an identical
  document, a retry or `submitEdit`. `ArticleIngestionTest` (direct executor `Runnable::run`, mocked port) checks that
  the flag off causes no extraction or port call, that the `ARTICLE` source causes no fetch, that an existing lesson is
  skipped while the other articles continue, and that a fetch failure on one URL lets the others continue.
- Update `hexagonal.md` §Domain Events with `DocumentAccepted` and its best-effort consumer.
