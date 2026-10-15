---
id: T20
title: How ingest progress reaches the browser, and the shape of run events
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — live progress cannot work as specified.

**1. Step events outside a transaction are silently dropped.** Phase 8 publishes `IngestStepCompleted` "inside the
corresponding commits" and listens with `@TransactionalEventListener(AFTER_COMMIT)`
(`docs/project/implementation-plan.md:737`). But Extract, Resolve, Write and the link check run outside any transaction
(the rule is never to call a model inside one). Spring discards an event published outside a managed transaction unless
`fallbackExecution = true`, so these steps would report nothing.

**2. The client has nothing to subscribe to.** Upload returns a document id (`implementation-plan.md:792`), but progress
is keyed by run id (`:743`, `:797`, and SPA 12.4 at `:998`). A queued upload has no run yet. Reverts and full Lints have
progress too.

**3. `DomainEvent` requires `UUID documentId()` on every event**
(`src/main/java/dev/mindforge/domain/model/DomainEvent.java:18`). REVERT and LINT runs have no document. Phase 3b.2
deletes four events but leaves the interface contract as it is.

**4. Phase order is inverted.** Phase 6 publishes run events (`docs/standards/architecture/hexagonal.md:124`) that are
only defined in Phase 8.1.

Decide:

- **Is progress a domain event?**
  - *Recommended:* no. Progress goes through a best-effort `ProgressNotifier` port called directly by the pipeline;
    domain events stay commit-bound.
  - *Alternative:* `fallbackExecution = true` on progress listeners.
- **The subscription key.** *Recommended:* one stream per knowledge base, `GET /api/knowledge-bases/{kbId}/progress`,
  which covers queued uploads, reverts and Lint. *Alternative:* per document.
- **The `DomainEvent` contract.** *Recommended:* drop `documentId()` from the sealed interface; each record carries its
  own ids.
- **Phase placement.** Move the run-event definitions into Phase 6.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option.

In short:
- Progress is not a domain event. It goes through a best-effort `ProgressNotifier` port, called directly by whoever runs
  the work, with one stream per knowledge base.
- The only run domain event is `IngestRunQueued`. It exists to wake the worker after commit.
- `DomainEvent` stops requiring a document id.

### Decisions

**1. Progress goes through a port, not the event bus.**

```java
public interface ProgressNotifier {                      // dev.mindforge.domain.port
    void notify(UUID kbId, RunProgress progress);        // best-effort; never throws
}
record RunProgress(UUID runId, RunKind kind, UUID documentId /* null for LINT and REVERT */,
                   RunStatus status,
                   String step /* extract|resolve|write|linkCheck|supersede|review; null on a status change */,
                   Integer done, Integer total, Instant at) {}
```

Who calls it, and when:
- `IngestPipeline` and `LintService` call it at each step boundary. During the write fan-out they also report progress
  through `done` and `total`.
- Status changes are notified **after** their transaction returns. The browser therefore never sees a `WRITTEN`,
  `COMPLETED` or `FAILED` that rolled back.
- `RevertService` notifies `COMPLETED` after its transaction commits.
- The upload, retry and Lint endpoints notify `QUEUED`.
- A fenced run (T17) notifies nothing.

This is chosen over `fallbackExecution = true`. That flag makes the same listener fire inside or outside a transaction,
depending on the caller — exactly the ambiguity the no-outbox rule exists to avoid. Domain events stay strictly
commit-bound.

**2. One stream per knowledge base:** `GET /api/knowledge-bases/{kbId}/progress` (SSE, ownership checked).

The adapter, `SseProgressNotifier`:
- keeps `Map<UUID kbId, Set<SseEmitter>>`, so several tabs work;
- removes emitters that have completed or failed;
- drops an emitter when a send to it fails.

What the stream carries:
- every run of that knowledge base — queued uploads, edits, retries, Lint and reverts;
- so a client can subscribe before a run even exists.

The stream is best-effort:
- On (re)connect, the SPA first reads `GET /api/knowledge-bases/{kbId}/runs`, then applies stream messages on top.
- A missed message is repaired by that read, never by redelivery.
- The single-instance ceiling applies, as it did for the old SSE registry.

**3. Domain events.** `DomainEvent` keeps `occurredAt()` and drops `documentId()`; each record carries its own ids. After
Phase 6 the sealed set is:

```java
record IngestRunQueued(UUID runId, UUID knowledgeBaseId, Instant occurredAt) implements DomainEvent {}
```

- `IngestRunQueued` is published in the transaction that inserts a `QUEUED` run (T17 decision 7). An `AFTER_COMMIT`
  listener calls the worker's `drain(kbId)`.
- `DocumentIngested` is kept from 3b to 6.8, because a sealed interface needs a permitted subtype. Phase 4.5 publishes
  it, and 6.8 replaces it with `IngestRunQueued`.
- `IngestStepCompleted`, `IngestRunCompleted` and `IngestRunFailed` are not added. Progress has its own port, and nothing
  else consumes the end of a run. They get added the day a real consumer appears.

**4. Phase placement.**
- The events, the `ProgressNotifier` port and the in-memory `SseProgressNotifier` move into **Phase 6.8**, where the
  pipeline first calls them.
- The endpoint joins **Phase 9.7**.
- Phase 8 has nothing left. Its number is retired and its heading points at 6 and 9, the same way Phase 7's Neo4j slot
  was handled.

### Feeds

- **T29**:
  - 3b.2: drop `documentId()`.
  - 6.8 and 6.9.
  - Phase 8: retire it.
  - 9.7: the progress endpoint.
  - 12.4: subscribe per knowledge base.
  - `hexagonal.md`: the domain-events section and its code example.
  - `architecture.md`: the data flow.
