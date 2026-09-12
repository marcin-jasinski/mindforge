---
id: T07
title: What replaces step-fingerprint checkpointing
type: grilling
status: closed
assignee: claude
blocked_by: [T04, T05]
---

## Question

Step fingerprinting is MindForge's entire reliability story: each step checksums its inputs,
prompt version and model id, and unchanged steps are skipped on rerun. `CLAUDE.md` makes it
non-negotiable that a pipeline checkpoint and its outbox event share one `@Transactional`
boundary.

That model assumes a step's output is a pure function of its input. Ingest is not: it mutates
a wiki that other documents have already changed, and *should* produce different results on
rerun because the wiki has moved on.

Decide:

- **What does re-uploading the same document do?** Skip as a duplicate (fingerprints demote to
  source-level dedup), or re-integrate against the current wiki? These give different products.
- **What does re-uploading a *revised* document do?** The old version's contributions are
  already woven into N pages. Is there a retraction story, or does the wiki just accumulate
  both and rely on supersession (T03)?
- **What is the transactional boundary** when Ingest writes 12 pages and fails at 5? Options:
  all-or-nothing (needs T05 to support it), page-at-a-time with a partially-updated wiki as a
  legal state, or a staging area that commits atomically.
- **Is a partially-ingested wiki a legal state?** If yes, `log.md` and the index have to be
  honest about it. If no, and pages are files, you need something transaction-shaped over
  object storage.
- **What survives of `StepCheckpoint`, `StepFingerprint` and `Hashes`?** They exist in the
  domain today.
- **Does the outbox rule still bind?** If Neo4j's projection changes shape (T09), what the
  outbox carries changes with it.

**Inherited from T04.** Three things land here.

1. **Step fingerprints have no home.** T04 deleted `Agent`, `AgentContext`, `AgentResult` and
   `AgentCapability`, and `AgentResult.Success.outputKey` indexed the accumulator T02 already killed.
   Whatever replaces checkpointing cannot hang off the agent interface, because there isn't one.
2. **Partial success is the settled semantics**, so idempotency is defined against it: a run generates
   bodies outside a transaction and commits in one, failed page writes are recorded on the run, and zero
   successes fails the run. Making a re-run idempotent against a partially-landed predecessor is this
   ticket's question — T03 already ruled that revert, not rollback, is the compensating action.
3. **Per-`KnowledgeBase` serialization needs a mechanism.** T04 fixed the constraint — one ingest run at a
   time per KB, queue the rest — because Resolve reads wiki state minutes before commit. Advisory lock,
   queue table or in-process semaphore is this ticket's call, and it interacts with whether the app ever
   runs more than one instance.

**Inherited from T05.** Storage is Postgres rows only, so the all-or-nothing / staging-area / object-storage
branches of this question are gone: T04's single commit transaction is simply a transaction.

- **The `ingest_runs` table is this ticket's.** `ingest_run_id` is referenced from `page_revisions`,
  `page_sources` and `page_supersessions`; its FK lands with the table.
- **Revert** is restore-forward revisions plus `DELETE … WHERE ingest_run_id = R` on sources and
  supersessions, per page, gated by the tip check (plus "slug is free" for a tombstone). Decide whether a
  revert is a new run or an action recorded on the original — the revisions it appends need *some* run id.
- **Serialization** has a Postgres row to hang on: `SELECT … FOR UPDATE` on `knowledge_bases` or a
  transaction-scoped advisory lock keyed on `kb_id` are both in reach. Decide which, and whether it spans
  generation or only commit.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**Nothing replaces step fingerprinting, because nothing is skipped: a re-run is a new run.** Idempotency moves to
the edges: an identical upload is deduplicated by a database constraint before any run exists; a revised
upload is a new `Document` of the same lesson and a new run against the current wiki. The ingest run is the
single record of what happened. A lease column on `knowledge_bases` serializes writers, and no outbox table
exists.

```sql
ingest_runs (run_id PK, knowledge_base_id FK→knowledge_bases CASCADE,
             kind VARCHAR NOT NULL,              -- INGEST | REVERT   (T10 may add LINT)
             document_id FK→documents NULL,      -- INGEST only
             reverts_run_id FK→ingest_runs NULL, -- REVERT only
             status VARCHAR NOT NULL,            -- RUNNING | WRITTEN | COMPLETED | FAILED
             failure_reason TEXT NULL, failures JSONB NOT NULL DEFAULT '[]',  -- per-page write failures
             supersession_skipped BOOLEAN NOT NULL DEFAULT FALSE,
             step_versions JSONB NOT NULL,       -- {"claimExtractor":"3","pageWriter":"5",...} + model ids
             cost NUMERIC NULL,                  -- never in an API response
             started_at, finished_at)
knowledge_bases  + active_run_id UUID NULL FK→ingest_runs   -- the lease
documents        - status                                   -- derived from runs
                 - UNIQUE (knowledge_base_id, lesson_id)
                 + UNIQUE (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION'
page_sources.document_id FK→documents ON DELETE RESTRICT
```

### The eight decisions

**1. An identical upload is a no-op, enforced by a constraint.** Same bytes into the same `KnowledgeBase` returns
the existing document id; no run starts. `UNIQUE (knowledge_base_id, content_hash)` enforces it under
concurrency, where a check-then-insert would race. Re-integrating identical content against a wiki that has
moved on buys churn and duplicate-page risk for an LLM bill, and nobody asked for it.

- **If that document's last run failed**, the upload still returns its id; the user retries it explicitly
  (a new run on the existing document). Dedup stays one rule with no exceptions, and a retry is an action, not a
  side effect of re-uploading.
- **Same bytes into a different `KnowledgeBase`** is a new document there.
- **Conversation turns are exempt** (partial index): "delete *Mitoza*" said twice, a week apart, is two edits.
- **Security fix, found in passing**: today's `DocumentRepository.findByContentHash(ContentHash)` takes no `kbId`,
  so dedup would hand user A the id of user B's document. It becomes `findByContentHash(kbId, hash)` (T12).

**2. A revised upload is a new `Document` of the same lesson and a new ingest run. There is no retraction.**

V3's `UNIQUE (knowledge_base_id, lesson_id)` goes: a lesson now has one `Document` per uploaded version, and
`LessonIdentity` is what versions share. The plan's "creates a new revision, invalidates stale checkpoints"
(Phase 4.5) is replaced by exactly this — there are no checkpoints to invalidate.

The new run revises `sources/<lesson-id>` (T06), so the Source Summary always digests the latest version, and
revises whatever Concept pages Resolve matches. Claims the new version **contradicts** are caught by the
Supersede step (T04). Claims the new version merely **dropped** stay in the wiki, still cited to the lesson.

**Retraction is rejected.** Bodies are opaque prose (T02), so there is no per-sentence provenance to subtract;
retracting a document means an LLM rewrite of every page citing it — an "un-ingest" Operation with its own
failure modes, reopening the settled Ingest / Query / Lint set. Removing knowledge on purpose is a conversation
edit (T03).

- `page_sources.document_id` is `ON DELETE RESTRICT`, and **no per-document delete exists**: deleting an upload
  while its prose lives on in pages would make the projected Citations lie. A `KnowledgeBase` delete still
  cascades everything.
- Cost accepted: a claim dropped from a revised document, and not contradicted by it, lingers silently.

**3. A crash commits nothing it had not already committed, and a startup sweep settles the rest.** Transaction
semantics are T04's — bodies in memory, one commit for pages, revisions, sources and the run; supersessions in a
second, short transaction. The run's status marks exactly where it got to:

| status | meaning | on restart |
|---|---|---|
| `RUNNING` | generating; nothing committed | → `FAILED` ("interrupted"), lease released |
| `WRITTEN` | pages committed; Supersede not yet committed | → `COMPLETED`, `supersession_skipped = true`, lease released |
| `COMPLETED` / `FAILED` | final | — |

A partially ingested wiki is a legal state (T04), and every projection of it is honest by construction: the
index is a query over live rows and `log.md` is a query over runs, so neither can claim pages that did not land.
Zero successful writes → `FAILED` in a transaction that records `failures` and releases the lease; nothing else
is written. A RelevanceGuard rejection is a `FAILED` run with that reason — a retry will not help and the SPA can
say so.

**4. `StepCheckpoint`, `StepFingerprint` and `step_checkpoints` are deleted; `Hashes` stays.** Fingerprints skip a
step whose inputs are unchanged; a re-run here is a new run against a moved wiki, so no step's inputs are ever
unchanged. The one saving on the table — reusing a failed run's claim set on retry — is a cache for a rare case.
`Hashes` stays: `ContentHash` uses it.

What a fingerprint *carried* survives as diagnosis rather than control flow: **`ingest_runs.step_versions`
records each service's `VERSION` and the model id per step.** That answers "which prompt wrote this page?" by
joining a revision to its run, and it is where prompt versioning lives now that `Agent.PROMPT_VERSION` is gone:
a `VERSION` constant on each of the four concrete services (CLAUDE.md's bump rule, unchanged), recorded on every
run. `DocumentStatus` and `documents.status` are deleted too — a second copy of run state that would drift from
the first. A document's state is its latest run.

**5. A revert is a run of its own.** `kind = REVERT`, `reverts_run_id = R`. The revisions it appends need a run id
(T05), and a separate run gets the report, the log line and the tip-only window for free. The original run is
never mutated — "R is reverted" is the existence of a completed REVERT run pointing at it. Reverting a revert is
therefore free, and correct, under the same tip check. A revert run adds no `page_sources`; it deletes R's
(T05 decision 4).

A conversation edit is a plain `INGEST` run whose document has `upload_source = CONVERSATION` (T03). No `EDIT`
kind: the source already says it. `UploadSource` gains `CONVERSATION`.

**6. Serialization is a lease column, claimed by a conditional update — not an in-process lock, not an advisory
lock.**

```sql
UPDATE knowledge_bases SET active_run_id = :runId
 WHERE kb_id = :kbId AND active_run_id IS NULL      -- 1 row: go; 0 rows: stay queued
```

- **Every page-writing run takes it**: ingest, conversation edit, revert, and Lint if T10 makes Lint write. A
  revert whose tip check raced an in-flight commit would otherwise restore a stale page.
- **The lease spans generation**, not just the commit: Resolve reads state minutes before commit (T04), so a lock
  around the commit alone would not stop two runs resolving against the same wiki.
- **It is released in the run's final transaction** (`COMPLETED` or `FAILED`), so success never leaks a lease.
- **The queue is rows that already exist.** A document with no run is pending. When a run releases the lease, the
  worker claims for the oldest pending document of that knowledge base; an upload that loses the claim just stays
  pending. No queue table.
- **Startup sweep** (decision 3) releases leases held by interrupted runs, then drains every knowledge base with
  pending documents.

Rejected: an **in-process lock** — the same amount of drain-and-sweep code, but invisible to the database and to
the SPA ("this knowledge base is busy"), and wrong the day a second instance starts. A **session advisory lock**
held across generation pins a pooled connection for minutes per active knowledge base. A **transaction-scoped
advisory lock** cannot span generation at all.

**Ceiling, named:** the startup sweep assumes one instance — it treats every `RUNNING` run as abandoned. Multiple
instances need a `lease_acquired_at` and an expiry instead. MindForge is single-instance today (Caffeine and the
in-memory SSE registry both already assume it).

**7. The outbox rule does not bind: no outbox table.** Implementation-plan Phase 8 already decided this —
`@TransactionalEventListener(AFTER_COMMIT)` and an in-memory SSE registry, with a full outbox deferred "for when
multiple independent consumers exist" — while `CLAUDE.md`, `hexagonal.md` and `architecture.md` still require a
`pipeline_events` outbox. The plan is right and the rule is stale.

Every consumer of a commit is derived and rebuildable from Postgres: the Neo4j projection, if T09 keeps one,
rebuilds from `page_links`; the Caffeine cache is in-process; SSE progress is best-effort by nature.
At-least-once delivery buys nothing when a missed event is repaired by a rebuild. The rule becomes: **a run's
commit and its domain events are published in the same `@Transactional` boundary; listeners run after commit and
must tolerate missing an event.**

`DomainEvent` is re-cut around runs: `DocumentIngested` stays (it wakes the worker after commit);
`PipelineStepCompleted` → `IngestStepCompleted(runId, step)`; `ProcessingCompleted(artifact)` →
`IngestRunCompleted(runId, kbId)`; `ProcessingFailed` → `IngestRunFailed(runId, reason, retryable)`;
`GraphProjectionUpdated` is T09's to keep or delete.

**8. The run record stores only what rows cannot tell.** Pages created and revised are **derived** from
`page_revisions` by `ingest_run_id` (revision 1 = created) — T02's "counted from the rows the transaction
inserted", literally. Stored: `failures` (a failed write leaves no row to count), `failure_reason`,
`supersession_skipped`, `step_versions`, `cost`, timestamps. `cost` joins the forbidden-response-fields list in
spirit: the run report shows counts and failures, never cost.

### Feeds

- **T08** — nothing new beyond T05's cache key; on-demand generation does not take the lease, because it writes
  no pages.
- **T09** — no outbox: a Neo4j projection, if kept, is fed by `AFTER_COMMIT` listeners and rebuilt from
  `page_links`, and must tolerate a missed event. `GraphProjectionUpdated` is yours to keep or delete.
- **T10** — a Lint that writes takes the lease and is a run: add `LINT` to `ingest_runs.kind`. A Lint scheduled
  after an ingest simply queues behind the lease.
- **T11** — `log.md` projects from `ingest_runs`: `INGEST` (Edit when the document is a conversation turn),
  `REVERT`, and `COMPLETED` runs with their derived counts; `FAILED` runs are omitted.
- **T12** — delete `StepCheckpoint`, `StepFingerprint`, `StepCheckpointEntity`, `StepCheckpointJpaRepository`, V5,
  `DocumentStatus` and `documents.status`. Change `DomainEvent` (decision 7), `UploadSource` (+`CONVERSATION`),
  `DocumentRepository.findByContentHash` (add `kbId` — cross-tenant bug), and V3's indexes. Keep `Hashes`,
  `ContentHash`.
- **T13** — `CLAUDE.md`'s "Pipeline checkpoint + outbox event in the same `@Transactional` boundary" is re-cut to
  decision 7's wording. `hexagonal.md`'s *Pipeline Idempotency* and *Transactional Outbox* sections and
  `architecture.md`'s *Idempotency & Reliability* section are rewritten. Phase 4.5's dedup/revision bullets and
  Phase 5's checkpointing are replaced; Phase 8 stands, re-pointed at run events.
- **Prompt-layer fog** — prompt versioning's home is settled: `VERSION` per service, recorded on each run.
