# The wiki lives only in PostgreSQL rows, and its history outlives deleted pages

Pages are rows in `wiki_pages`. The OKF bundle is never materialized — not on disk, not in
object storage, not as a cache — and exists only as an export. `StoragePort` plays no part.

`page_revisions` is an append-only, post-write snapshot of every write, kept forever and stamped
with its ingest run. So are `page_sources` and `page_supersessions`, so reverting a run is
restore-forward revisions plus a delete by run id. **Deleting a page** appends a tombstone
revision and removes the `wiki_pages` row, while the three history tables deliberately have **no
foreign key to `wiki_pages`**: every live read already joins through `wiki_pages`, so the missing
row does the filtering, and revert of a deletion re-inserts the row with its history intact.

Tenancy is structural: every table carries `knowledge_base_id`, links use a composite key so
they cannot cross knowledge bases, and `WikiStore` scopes every call by `kbId` without
authorizing.

## Considered Options

- **Files behind object storage with a Postgres index**: cannot join the single commit
  transaction Ingest needs, and pushes ownership into path discipline.
- **Soft delete (`deleted_at`)**: the same revertibility, bought with a filter on every read path
  in the system.
- **Pruned history (keep N, keep since)**: breaks run-report diffs, and keep-since can age out the
  revision a tip-only revert needs.
- **Row-level security**: a session variable to set correctly on every pooled connection is more
  machinery than a composite key.

## Consequences

- Orphan history rows exist on purpose.
- Export renders the whole bundle every time.
- Full-text search over bodies is available without a new store.

Decided in [T05](../wayfinder/tickets/05-wiki-storage.md).
