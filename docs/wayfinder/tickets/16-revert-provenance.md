---
id: T16
title: What revert does to provenance, supersessions and history
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — T07 states a property ("reverting a revert is free, and correct")
that the storage design does not have.

**1. Revert deletes sources for pages it does not restore.** Revert of run R deletes *all* of R's `page_sources` and
`page_supersessions` (`docs/project/architecture.md:125`, `docs/wayfinder/tickets/05-wiki-storage.md:135`,
`docs/project/implementation-plan.md:588`). Revert is tip-only, so the pages R no longer tips keep text that still
carries R's content. Deleting their source rows leaves them citing too little, and drops them from the Lesson study
scope (T08 decision 2).

**2. Reverting a revert cannot restore what the first revert deleted.** T07 decision 5
(`docs/wayfinder/tickets/07-idempotency-and-failure.md:159`) calls it free and correct. But the first revert
hard-deleted R's source and supersession rows, and a REVERT run adds none. Undoing the undo restores the text without
its citations or supersessions.

**3. Removing a single supersession leaves no trace.** T03's per-row removal (`implementation-plan.md:589`) takes no
lease and creates no run or record. It cannot be undone, and it can race the Supersede step's commit 2.

**4. `log.md` is meant to be history, but some of it is recomputed from deletable rows.** "N claims superseded" is
derived from `page_supersessions` (`docs/wayfinder/tickets/11-bundle-export.md:129`, `:135`), and revert or per-row
removal deletes those rows, so old log lines change. The vocabulary also only covers "undid the ingest of …" — there is
no wording for reverting a Lint, an Edit or a Revert.

**5. Privacy: uploaded text is kept forever.** No per-document delete exists (`tickets/07-…:124`). After a document's
only run is reverted, nothing references it, yet its full text stays in `documents` until the whole knowledge base is
deleted.

Decide:

- **Which source rows a revert deletes.** *Recommended:* only those for pages it actually restores.
- **Reverting a revert.**
  - (a) Soft-retire rows with `reverted_by_run_id` — reintroduces the read filter T05 rejected.
  - (b) Snapshot the removed rows on the REVERT run so a later revert can re-insert them.
  - (c) Forbid reverting a REVERT run and amend T07's claim. *Recommended — retry or re-upload covers the need.*
- **Per-row supersession removal.** Takes the lease (409 if busy)? Leaves a record?
- **Stable log counts.** *Recommended:* store the supersession count on the run at commit 2, since a deleted row cannot
  tell (the T07 decision 8 principle). Add wording for reverting Lint, Edit and Revert runs.
- **Document deletion.** Allow deleting a `Document` once no `page_sources` row references it, or accept retention and
  document it.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option.

**In short:**

- A revert removes provenance only for the pages it restores.
- It runs as a single transaction under the lease, and a REVERT run cannot itself be reverted.
- Removing one supersession is also a REVERT run.
- Counts that rows cannot reproduce are stored on the run.
- Uploaded text is retained, and that retention is documented.

### Decisions

**1. Revert deletes sources and supersessions only for the pages it restores.** Take run R, and let P be the set of pages
R still tips — T05's tip check, plus "path free" for a tombstone. The revert deletes:

- R's `page_sources` rows where `page_id ∈ P`;
- R's `page_supersessions` rows where `superseding_page_id ∈ P`.

A supersession belongs to the prose that carries the correction. If that prose outlives the revert, the supersession is
still true.

Pages R no longer tips still carry R's content. So they keep R's citations, their Lesson scope membership (T08) and their
supersessions. This amends T05 decision 4 and T07 decision 5, from "deletes R's" to "deletes R's for the pages it
restores". Revert is offered while P is non-empty.

**2. A REVERT run cannot be reverted** (option c). T07's "reverting a revert is free, and correct" is withdrawn: the first
revert hard-deleted rows that a second one could not bring back. To re-apply the work instead:

- **a reverted ingest** — retry its document (T17), which makes a new run against the current wiki;
- **a reverted edit** — send it again as a new conversation turn;
- **a reverted Lint** — run Lint again.

Options (a) and (b) would each build new machinery — a read filter, or row snapshots — to save the user a retry.

**3. Removing one supersession is a REVERT run scoped to one row.** The run has `kind = REVERT` and `reverts_run_id` set
to the run that inserted the row. It restores no pages, deletes that one `page_supersessions` row and sets
`supersession_count = 1`.

- **It takes the lease** the way every revert does (decision 4). It returns 409 while any run is active, so it cannot
  race commit 2.
- **It leaves a record:** the run, its report and a `log.md` line.
- **It cannot be undone**, like every revert. A wrongly removed supersession returns if the document is retried.

No new kind is needed, because "undo part of run R" is what `REVERT` already means.

**4. A revert runs in one transaction.** It makes no model call, so nothing forces generation outside a transaction. In
order:

1. Insert the `REVERT` run.
2. Claim the lease with the conditional update. 0 rows rolls back and returns 409.
3. Run the tip checks, write the restore-forward revisions, and do the deletes above.
4. Set `supersession_count` and `status = COMPLETED`, release the lease, and commit.

Nothing is ever left `RUNNING`, so the sweep never sees a revert.

**5. Stable log counts.** `ingest_runs.supersession_count INT NOT NULL DEFAULT 0` holds the number of rows a run inserted
at commit 2 (`INGEST`) or deleted (`REVERT`). Page counts stay derived from `page_revisions`, which are never deleted.

The final vocabulary amends T11 decision 4. Only non-zero counts are listed:

```markdown
* **Ingest**: [Biologia — lekcja 3](/sources/biologia-lekcja-3.md) — 2 created, 5 revised, 1 claim superseded.
* **Edit**: conversation — 1 revised, 1 deleted.
* **Lint**: links added to 12 pages.
* **Revert**: undid the ingest of 2026-09-09 ([Biologia — lekcja 2](/sources/biologia-lekcja-2.md)) — 3 pages restored, 2 pages removed, 1 supersession removed.
* **Revert**: undid the edit of 2026-09-09 — 1 page restored.
* **Revert**: undid the Lint of 2026-09-09 — 12 pages restored.
* **Revert**: removed 1 supersession from the ingest of 2026-09-09 ([Biologia — lekcja 2](/sources/biologia-lekcja-2.md)).
```

How each count is derived from the run's revisions:

| Count | Revisions it counts |
|---|---|
| `created` | revision 1 |
| `revised` | later, non-tombstone revisions |
| `deleted` | tombstones |
| `restored` | a REVERT run's non-tombstone revisions |
| `removed` | a REVERT run's tombstones |

**Lint** counts pages, not links, because a Lint revision is a page with inserted links. So no link counter is stored.
There is no wording for reverting a revert, because it cannot happen.

**6. Uploaded text is retained, and per-document erasure is not built.** Erasing a document's `original_content` would
not erase its knowledge. The prose it produced lives in `page_revisions`, which are kept forever (T05 decision 3), and in
every page that still carries it. A per-document erase button would promise privacy it cannot deliver. Real erasure means
deleting the knowledge base, which cascades everything (T24).

This is documented in `architecture.md`'s trust model and in ADR 0014. Re-add condition: a user asks to erase one source.
That means pruning its revisions too, so design it then rather than build a text-only erase now.

### Feeds

- **T17** — revert and supersession removal are synchronous and return 409 when busy; retry is how work is re-applied.
- **T25** — `WikiStore.deleteSources(kbId, runId, pageIds)`, `deleteSupersessionsBySuperseding(kbId, runId, pageIds)`,
  `deleteSupersession(kbId, id)`.
- **T29** — 5.6 re-cut; ADR 0012 amended; the `LogRenderer` vocabulary; `ingest_runs.supersession_count`; a consequence
  line in ADR 0014.
