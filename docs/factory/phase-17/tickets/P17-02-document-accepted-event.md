---
id: P17-02
title: Publish DocumentAccepted in the upload transaction when an upload inserts a new document
status: ready
plan_tasks: [17.2]
decisions: [D01]
depends_on: []
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
`DomainEvent` has a second event, `DocumentAccepted`. `IngestionService.ingest` publishes it inside the upload
transaction, only on the branch that inserts a new document. No other path publishes it. Nothing listens yet (the
listener is P17-08).

## Context
- `src/main/java/dev/mindforge/domain/model/DomainEvent.java`: `sealed interface DomainEvent permits
  DomainEvent.IngestRunQueued`; one record `IngestRunQueued(UUID runId, UUID knowledgeBaseId, Instant occurredAt)`.
- `src/main/java/dev/mindforge/application/service/IngestionService.java`: `ingest(DocumentUpload)` admits, parses,
  resolves the lesson and hashes outside the transaction; then `transactions.execute(...)` locks the knowledge base,
  returns an identical document's id, applies the lesson rule, or inserts a `Document` and calls `enqueue`
  (which publishes `IngestRunQueued`, ~line 215). `submitEdit`, `retry` and `enqueue` are other paths.
- Events go through the `EventPublisher` port (Spring `ApplicationEventPublisher` adapter); listeners use
  `@TransactionalEventListener(AFTER_COMMIT)` (`infrastructure/event/RunWorkerTriggers.java`). No code switches over
  `DomainEvent` exhaustively today (only `RunWorkerTriggers.onRunQueued(DomainEvent.IngestRunQueued)` listens).
- ADR 0024 (`docs/adr/0024-articles-fetched-after-upload-commit-from-a-document-event.md`, Accepted) and ADR 0015
  (events in the commit's transaction, best-effort listeners, no outbox).
- Existing unit test: `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java` (Mockito ports).

## Decisions to apply
**D01 — Decision:** Add `DomainEvent.DocumentAccepted` and publish it in the upload transaction of
`IngestionService.ingest`, only on the branch that inserts a new document. An `AFTER_COMMIT` listener in
infrastructure hands the work to the virtual-thread `runExecutor` (P17-08). Retry and sweep never fetch again.

**D01 — Implementation notes (this ticket's part):**
- `domain/model/DomainEvent.java`: `sealed interface DomainEvent permits IngestRunQueued, DocumentAccepted`;
  `record DocumentAccepted(UUID documentId, UUID knowledgeBaseId, UUID uploadedBy, UploadSource source, Instant occurredAt)`.
  Javadoc: a new document was accepted; its best-effort listener may fetch the articles it links to after commit.
- `IngestionService.ingest`: publish `DocumentAccepted` inside the transaction, after `documents.insert`, only on the
  insert branch. Do not publish it for an identical document, in `submitEdit`, in `retry` or in `enqueue`.
- Tests: `IngestionServiceTest` checks that the event is published only for a new document, and not for an identical
  document, a retry or `submitEdit`.

## Acceptance criteria
- [ ] AC1: `DomainEvent.DocumentAccepted` exists with the five components above, JDK imports only, and the sealed
  `permits` clause lists both events.
- [ ] AC2: A first upload of new content publishes exactly one `DocumentAccepted` carrying the new document id, the
  knowledge base id, the uploader and the upload's `UploadSource`, plus the existing `IngestRunQueued`; it is published
  inside the transaction callback (before it returns).
- [ ] AC3: An upload identical to an existing document publishes no `DocumentAccepted`.
- [ ] AC4: An upload refused by the lesson rule (`LessonAlreadyExistsException`) publishes no `DocumentAccepted`.
- [ ] AC5: `submitEdit` and `retry` publish no `DocumentAccepted`.
- [ ] AC6: All existing tests pass unchanged in behaviour.

## Test plan
- Unit (Mockito, AssertJ, no Spring): extend `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java`
  for AC2–AC5 (capture `EventPublisher.publish` arguments; `TransactionOperations` run inline).
- AC1 is covered by compilation and the AC2 assertions on the record fields.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/DomainEvent.java`
- `src/main/java/dev/mindforge/application/service/IngestionService.java`
- `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java`

## Out of scope
- The listener `ArticleFetchTriggers` and the `ArticleFetcher` (P17-07, P17-08).
- The article entry method `ingestArticle` (P17-03).
- Docs (`hexagonal.md` § Domain Events is updated in P17-10).

## Notes
