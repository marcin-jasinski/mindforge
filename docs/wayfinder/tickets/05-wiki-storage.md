---
id: T05
title: Where the wiki bundle physically lives
type: grilling
status: closed
assignee: claude
blocked_by: [T02, T03]
---

## Question

MindForge has PostgreSQL as source of truth, a `StoragePort` (filesystem in dev, S3 in prod)
for uploaded files, Neo4j as a derived projection, and Caffeine in front. A wiki bundle fits
none of them cleanly.

Where do pages live?

- **Postgres rows**, rendered to markdown only on export. Gives transactions, ownership
  queries, page history via a versions table, and joins to `Document` for provenance. Costs:
  markdown becomes a projection, and every OKF nicety (`index.md`, `log.md`, bundle-relative
  links) is synthesized rather than stored.
- **Files behind `StoragePort`**, with Postgres holding only an index. The bundle on disk *is*
  the OKF bundle; export is a zip of a directory. Costs: no transactions across a multi-page
  write (feeds T07), ownership enforcement moves into path discipline, and history needs
  something git-shaped that the map has ruled out as a *backend*.
- **Hybrid**: page bodies as text columns, bundle materialized on export.

Whatever wins, the seam is a **`WikiStore` port** in `dev.mindforge.domain.port` — the demo's
ADR 0011 found the same seam and it is the one place backends diverge. Decide its surface:
does it speak pages (`getPage`, `putPage`, `listPages`) or file primitives (`read`, `write`,
`list`, `grep`)? T04 constrains this — a tool loop wants primitives, a typed pipeline wants pages.

Also settle:

- **How cross-links are stored.** Demo ADRs 0015/0017/0021: the bundle-relative form
  (`[title](/path/page.md)`) is canonical above the store, and each store rewrites on write and
  reverses on read. If pages are Postgres rows, "the stored link form" is a real question, not
  a theoretical one.
- **Page history.** T03 said automatic, so this is required, not optional. `PageRevision` retention must
  support **tip-only run revert**: every page a run touched needs its pre-run revision retrievable until a
  later run touches that page. "Keep N" is viable; "keep current only" is not. Revert is restore-forward —
  it writes a new revision carrying the old body and never deletes revision rows, so `revision` stays
  monotonic for T08's cache key.
- **Multi-tenancy.** Every read and write is scoped to one bundle owned by one user. This is
  the hard boundary, not a filter you remember to apply.


**Inherited from T02.** `WikiStore` speaks pages, not file primitives, and every method takes `kbId`
as its first argument — multi-tenancy is structural rather than a filter to remember. `PageRevision`
is named in the domain but its **retention policy** (keep all, keep N, keep since) is this ticket's
call, because that is where the storage cost is visible. Page bodies are prose only: frontmatter,
`index.md` and `log.md` are projected on export, so the store never holds them.

**Inherited from T04.** The pages-versus-primitives question is closed: with no tool loop there is no
caller for `read` / `write` / `list` / `grep`, so `WikiStore` speaks pages only, as T02 chose. Two more
constraints land here. Bodies are generated outside any transaction and the whole run commits in one
`@Transactional` boundary, so the store must support writing N pages, their revisions, the run record and
the outbox event together. And ingest runs **serialize per `KnowledgeBase`** — the constraint is T04's, the
mechanism (advisory lock, queue table, in-process semaphore) is T07's, but whichever it is has to sit
where this ticket puts the store.

## Answer

**Pages are Postgres rows and nothing else.** The OKF bundle exists only as an export output. History is
an append-only revision log, kept forever, and every row a run adds beside a body is stamped with that run
so revert is a delete by run id. Deleted pages lose their live row but not their history.

Q1–Q4 were answered by the user. Q5–Q8 were taken on the recommended option under the user's standing
instruction to proceed with recommendations.

```sql
wiki_pages          (page_id PK, knowledge_base_id FK→knowledge_bases CASCADE, slug, title, page_type,
                     markdown_body TEXT NOT NULL, revision INT, created_at, updated_at,
                     UNIQUE (knowledge_base_id, slug), UNIQUE (knowledge_base_id, page_id))
page_links          (knowledge_base_id, source_page_id, target_slug, fragment,
                     FK (knowledge_base_id, source_page_id) → wiki_pages CASCADE,
                     INDEX (knowledge_base_id, target_slug))
page_revisions      (page_id, revision, knowledge_base_id FK→knowledge_bases CASCADE, ingest_run_id,
                     title, page_type, markdown_body TEXT NULL, created_at, PK (page_id, revision))
page_sources        (page_id, ingest_run_id, document_id, knowledge_base_id FK→knowledge_bases CASCADE,
                     PK (page_id, ingest_run_id))
page_supersessions  (supersession_id PK, knowledge_base_id FK→knowledge_bases CASCADE,
                     superseded_page_id, section_anchor, superseding_page_id, ingest_run_id, created_at)
-- revisions, sources, supersessions: deliberately NO FK to wiki_pages (decision 5)
-- ingest_run_id FKs land with the ingest_runs table, which T07 shapes
```

```java
record PageRevision(UUID pageId, int revision, UUID ingestRunId,
                    String title, PageType type, String markdownBody,  // null = tombstone
                    Instant createdAt) {}
record PageSource(UUID pageId, UUID documentId, UUID ingestRunId) {}
record PageSupersession(UUID supersessionId, UUID supersededPageId, String sectionAnchor,
                        UUID supersedingPageId, UUID ingestRunId, Instant createdAt) {}
```

### The eight decisions

**1. Postgres rows only — no materialized bundle anywhere.** `wiki_pages.markdown_body TEXT` is the live
prose. No on-disk copy, no cached export, no S3 object. The three options in the question were really one:
"hybrid" *is* the rows option once T02 made frontmatter, `index.md` and `log.md` projections, and the files
option cannot join T04's single commit transaction. Every earlier decision already assumed rows — link
resolution by join (T02), one `@Transactional` commit (T04), revert as a query (T03).

`StoragePort` plays no part in the wiki. It does not exist in code today (`tech-stack.md:97` and
`architecture.md:52` only), and uploaded sources already live in `documents.original_content` /
`content_blocks`. Whether binary uploads still need it is T12's call. `WikiStore` has one adapter, JPA; the
port exists because `dev.mindforge.domain` needs one, not because a second backend is coming.

Cost accepted: export renders the whole bundle every time. That is string rendering, and T11 owns it.

**2. A revision is an append-only, post-write snapshot of `title`, `type` and body, stamped with its run.**
Every write — creation included — appends a revision holding the state *after* the write, so the newest
revision duplicates the live row. That duplication buys the two questions revert asks for free:

- **Tip check**: does the page's highest revision carry this run's id? One comparison.
- **Pre-run state**: revision `r − 1`. If the run's revision is `1`, the run created the page.

Title and type are snapshotted because a run may change them; a body-only snapshot would revert the prose
under the new title — a silent half-revert. Slug is not snapshotted: it is identity, and renames are T06's.
Restore-forward appends revision `r + 1` carrying `r − 1`'s content, so `revision` stays monotonic for T08.
`wiki_pages.revision` and the highest `page_revisions.revision` are written in the same transaction and
always agree.

Rejected: revisions holding only overwritten states. No duplication, but the live row then needs its own
`lastRunId`, and "what did run R write?" becomes a diff against a successor.

**3. Keep every revision. No pruning, no retention config.** Keep-2 satisfies revert alone, but T03's run
report diffs each written page against its prior revision, and pruning would make that report complete for
some runs and not others depending on how busy a page has been since. Keep-since is rejected on correctness,
not cost: a quiet page's `r − 1` can age out while its run is still the tip, breaking revert. A page revised
500 times at ~10 KB is ~5 MB of TOAST-compressed `TEXT`. Add pruning when a real `KnowledgeBase` shows the
table costing something.

**4. `PageSource` and `PageSupersession` carry `ingestRunId`; revert deletes by run.** Revert of run R is
Q2's restore-forward revisions plus `DELETE … WHERE ingest_run_id = R` on both tables. Without the stamp,
reverting a re-ingest of document D could not tell whether D had contributed to a page *before* R.

- `PageSource` becomes one row per (page, run); export's `source_docs:` is `DISTINCT document_id`. It is
  honestly redundant with revisions — every (page, run) source row has a matching revision — and kept anyway:
  the alternative derives provenance from revisions and must filter out reverted runs on every read, which is
  the filter-you-must-remember T02 designed away, and reopens T02's join-table decision.
- `PageSupersession` gains a surrogate `supersessionId`, because T03's run report removes supersessions one
  row at a time.
- `PageLink` carries no run id. It is derived from the body and re-parsed whenever a body is written,
  restore-forward included.

**5. Deletion hard-deletes the page row; history outlives it.** A deletion — a user's "delete *Mitosis*" run
(T03) or revert of a creating run — appends a **tombstone revision** (`markdown_body` null) and deletes the
`wiki_pages` row. `page_links` cascades with it (derived). `page_revisions`, `page_sources` and
`page_supersessions` reference `page_id` **without an FK to `wiki_pages`**, keeping only their FK to
`knowledge_bases` so a bundle delete still cascades everything.

Every live consumer joins through `wiki_pages`, so the absent row does the filtering: inbound links dangle,
supersession markers involving the page stop rendering, T08's skip-join stops skipping — with no
`deleted_at IS NULL` anywhere. Revert of a deletion re-inserts the row under the same `page_id` from revision
`r − 1`, and its sources and supersessions are still there. Both halves of T03 now hold literally: revision
rows are never deleted, and revert deletes pages the run created.

**Slug reuse.** A later run that creates a new *Mitosis* gets a new `page_id`, and the old deletion stops
being revertible — "slug is free" is part of the tip check for a tombstone, and `UNIQUE (knowledge_base_id,
slug)` refuses the re-insert even if the check is forgotten.

Rejected: soft delete. Same revertibility, bought with a `deleted_at` filter on Query, the SPA, export, the
index projection, the dangling join, the supersession marker and T08's skip-join, plus a partial unique index.

Cost accepted: deliberate orphan rows in three history tables, and no referential integrity from them to the
page. History describing a thing that no longer exists is what history is.

**6. Tenancy is a column and a composite key, not row-level security.** Every wiki table carries
`knowledge_base_id NOT NULL` with an FK to `knowledge_bases ON DELETE CASCADE`. `page_links` references
`(knowledge_base_id, source_page_id)` against `UNIQUE (knowledge_base_id, page_id)`, so a link row claiming one
bundle for a page in another is a constraint violation, not a bug to catch in review. Every `WikiStore`
method takes `kbId` first (T02) and every adapter query binds it.

The store **scopes; it does not authorize.** "Does this user own this `KnowledgeBase`?" is checked once per
request in the controller or application service, as `CLAUDE.md` already requires; the store then cannot be
asked a question that spans bundles. Rejected: Postgres RLS — one application role, one database user, and a
session variable to set correctly on every pooled connection is more machinery than the composite key.

**7. The store holds links in canonical form and never rewrites them.** The body stores exactly what T02
specified — bundle-relative `[Title](/dir/slug.md#fragment)` — and the adapter parses `target_slug` and
`fragment` into `page_links` on write. The demo's per-store rewrite-on-write / reverse-on-read existed
because its stores disagreed on what a path means; there is one store now, and its stored form *is* the
canonical form. Rewriting to an SPA route (`/kb/{id}/page/{slug}`) happens on the way to the browser, at the
API or SPA; export emits the body verbatim (T11). Dangling targets are never rewritten into anything — the
dangling report is `page_links LEFT JOIN wiki_pages … WHERE page_id IS NULL`.

**8. The commit transaction lives in the application service, as it does today.** `WikiStore` methods are
page-shaped and fine-grained (`findBySlug`, `listPages`, `savePage`, `appendRevision`, `addSources`,
`addSupersessions`, `deleteRuns…` — the exact list is T13's to write, not a decision); the Ingest service's
commit method is `@Transactional` and calls them together with the run record and the outbox, exactly the
pattern `hexagonal.md` already documents. No `commitRun(bundle)` god-method on the port: it would move the
transaction boundary into the adapter to protect against a caller who does not exist.

Because the store is Postgres, T04's per-`KnowledgeBase` serialization has a row to hang on — a
`SELECT … FOR UPDATE` on the `knowledge_bases` row or a transaction-scoped advisory lock keyed on `kb_id` are
both in reach. Which one, and whether it covers the minutes of generation or only the commit, is T07's.

### Feeds

- **T06** — slug uniqueness is `UNIQUE (knowledge_base_id, slug)`: slugs are unique per bundle, not per
  directory. The canonical link form still carries a directory (`/concepts/slug.md`), so T06 must decide
  whether the directory is identity or decoration — if it is derived from `type`, a type change leaves a
  stale path in every inbound body. Renames are not snapshotted by revisions.
- **T07** — owns the `ingest_runs` table that `ingest_run_id` references in four tables, the shape of a
  revert (a new run, or an action recorded on the original), and the serialization mechanism, with a Postgres
  row lock and an advisory lock both available. Revert is per page, gated by the tip check (plus "slug free"
  for tombstones).
- **T08** — `revision` is monotonic across revert, so it is a safe cache key. The superseded-claim skip is an
  inner join through `wiki_pages`, so supersessions involving a deleted page stop applying without a filter.
- **T09** — bodies are Postgres `TEXT`, so full-text search on `wiki_pages` is available to both Resolve and
  Query's lexical tier without a new store; `page_links (knowledge_base_id, target_slug)` is indexed for the
  graph tier.
- **T10** — the dangling-link report is one left join; a supersession whose `section_anchor` no longer matches
  a heading, or whose superseding page is gone, is the same shape of query.
- **T11** — export reads rows and renders; there is no directory to zip. Tombstoned pages are simply absent.
- **T12** — no `StoragePort` for the wiki; `ArtifactRepository` is replaced by `WikiStore`; five new tables,
  no pending or approval tables. Whether `StoragePort` survives for binary uploads is T12's.
