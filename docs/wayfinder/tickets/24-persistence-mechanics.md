---
id: T24
title: Foreign-key actions, the dedup insert, and knowledge-base deletion
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Gap** — the schema as specified can fail at runtime.

**1. `page_sources.document_id ON DELETE RESTRICT` can block knowledge-base deletion**
(`docs/wayfinder/tickets/07-idempotency-and-failure.md:91`, `docs/project/implementation-plan.md:563`). RESTRICT is
checked immediately and cannot be deferred. Deleting a knowledge base cascades to both `documents` (V3) and
`page_sources` (FK to `knowledge_bases`), so whether the delete succeeds depends on which cascade fires first. A plain,
non-deferred NO ACTION is checked at the same point inside nested cascades, so it does not help either.

**2. Deleting a knowledge base mid-run is unspecified.** `knowledge_bases.active_run_id → ingest_runs → knowledge_bases`
is a cycle. If a knowledge base is deleted while its run is generating, the worker's later commits hit FK violations.
`KnowledgeBaseController`'s "delete cascades everything" (`implementation-plan.md:795`) says nothing about a busy
knowledge base.

**3. The dedup mechanism is missing.** "Enforced by the constraint, not check-then-insert" (`implementation-plan.md:510`)
leaves these open:
- a unique violation inside the `@Transactional` save aborts the PostgreSQL transaction, so the existing id cannot then
  be looked up — and `DocumentIngested` is published in that same transaction;
- Spring Data's `save()` on an entity with an assigned UUID runs `merge` (select, then insert);
- `ON CONFLICT` against a partial unique index must repeat the index predicate.

**4. `knowledge_bases.document_count` has no maintainer.** It is a stored counter (`V2__create_knowledge_bases.sql:6`)
that survives into the baseline (`docs/wayfinder/tickets/12-existing-code-fate.md:185`), while `pageCount` is derived at
read time (`tickets/12-…:126`).

Decide:

- **The FK action.** *Recommended:* `NO ACTION DEFERRABLE INITIALLY DEFERRED`, plus an integration test that deletes a
  knowledge base with pages, sources and runs.
- **Deleting a busy knowledge base.** *Recommended:* 409 while `active_run_id` is set.
- **The dedup insert.** *Recommended:* a native
  `INSERT … ON CONFLICT (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION' DO NOTHING RETURNING
  document_id`. If no row comes back, select the existing id. Publish `DocumentIngested` only when a row was inserted.
- **`document_count`.** *Recommended:* drop it from the V1 baseline and derive it.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. The dedup insert is simpler than the
recommendation, because T18 already serializes uploads.

### Decisions

**1. Cross-cascade foreign keys are `DEFERRABLE INITIALLY DEFERRED`.** One rule applies to the whole schema rather than FK
by FK: *a foreign key whose referencing and referenced rows are both removed by the same cascade is declared
`NO ACTION DEFERRABLE INITIALLY DEFERRED`.* Such a key is checked at commit, after the whole cascade has run, so the order
in which PostgreSQL fires cascades no longer matters.

The rule covers these keys:

| FK | Why both sides cascade |
|---|---|
| `page_sources.document_id → documents` | both cascade from `knowledge_bases` |
| `ingest_runs.document_id → documents` | same |
| `ingest_runs.reverts_run_id → ingest_runs` | same table, same cascade |
| `page_revisions`, `page_sources`, `page_supersessions` `.ingest_run_id → ingest_runs` | both cascade from `knowledge_bases` |
| `documents.uploaded_by → users` | a user delete cascades through `knowledge_bases` to `documents` |

Deferred `NO ACTION` still refuses a lone delete of a referenced document at commit. That was all T07's `RESTRICT` was for,
since no per-document delete exists (T16).

**Integration tests** (Testcontainers):

- Delete a knowledge base holding pages, links, revisions, sources, supersessions, runs, cards and study events. Every
  table is then empty for it.
- Delete a user who owns such a knowledge base. The result is the same.

**2. A busy knowledge base cannot be deleted.**

```sql
DELETE FROM knowledge_bases WHERE kb_id = :kb AND active_run_id IS NULL
```

- If this deletes 0 rows while the knowledge base exists, the answer is **409** `KnowledgeBaseBusyException`.
- `QUEUED` runs go with the cascade.

The conditional delete and T17's claim both write the same row, so they serialize. A worker whose run was queued cannot
start in a deleted knowledge base: its claim updates 0 rows.

The `active_run_id → ingest_runs → knowledge_bases` cycle is harmless. The only delete that could break it refuses while
the link is set.

**3. The dedup insert is check-then-insert under the knowledge-base row lock.** T18's lesson rule already opens the upload
transaction with `SELECT … FROM knowledge_bases WHERE kb_id = :kb FOR UPDATE`, which serializes every upload into that
knowledge base. Under that lock:

1. Select the document by `(knowledge_base_id, content_hash)`, excluding conversation turns. If found, return its id
   (202) — no run, no event.
2. Apply the lesson rule (T18).
3. Insert the `Document`. From Phase 6, also insert its `QUEUED` run and publish `IngestRunQueued` (T17, T20).

`UNIQUE (knowledge_base_id, content_hash) WHERE upload_source <> 'CONVERSATION'` stays as the backstop. A violation there
is a bug and surfaces as a 500.

This amends 4.5's "not check-then-insert". T07 rejected check-then-insert because it races; under the lock it does not.

No native `ON CONFLICT` insert is needed. For JPA inserts, `DocumentEntity` implements `Persistable<UUID>` with `isNew()`
true until persisted. So `save()` issues one `INSERT` instead of `merge`'s select-then-insert. The same applies to every
entity with an application-assigned UUID.

**4. `knowledge_bases.document_count` is dropped from the baseline, and counts are derived at read.**

- In 3b, `KnowledgeBase.documentCount`, the entity field and `KnowledgeBaseResponse.documentCount` go.
- Phase 9's knowledge-base view derives document and page counts with one grouped query, the way `pageCount` already is
  (T12).

### Feeds

- **T29**:
  - 3b: baseline without `document_count`, plus the three `KnowledgeBase` files;
  - 4.5: dedup under the lock;
  - 5.2: the FK rule;
  - 5.7 and 9.9: deletion tests;
  - 9.6 and 9.7: 409 on delete;
  - `models.md`: the `Persistable` note and the deferred-FK rule.
