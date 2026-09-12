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

## Amendments

2026-09-12, from the spec review:

- **Uploaded text is retained** while its knowledge base exists
  ([T16](../wayfinder/tickets/16-revert-provenance.md)). Erasing one document's text would not erase what it taught —
  that prose lives in revisions kept forever — so erasure is deleting the knowledge base.
- **Cross-cascade foreign keys are `DEFERRABLE INITIALLY DEFERRED`**, so a knowledge-base or user delete succeeds
  regardless of cascade order. A knowledge base with an active run cannot be deleted (409)
  ([T24](../wayfinder/tickets/24-persistence-mechanics.md)).
- Reads that join across tables live on consumer-named query ports beside `WikiStore`, and `kbId` comes first on every
  tenant-scoped port ([T25](../wayfinder/tickets/25-port-read-surface.md)).
- A revert deletes sources and supersessions only for the pages it restores (T16), which amends "a delete by run id"
  above.
