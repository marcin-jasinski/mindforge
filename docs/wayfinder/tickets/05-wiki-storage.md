---
id: T05
title: Where the wiki bundle physically lives
type: grilling
status: open
assignee:
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

## Answer
