## Database Migrations

### Reversible
Design migrations to be safe to run forward-only. Flyway Community Edition does not support undo migrations. Every migration must be non-destructive by default; compensating up-migrations handle corrections.

### Small and Focused
Keep each migration to a single logical change.

### Zero-Downtime Awareness
Consider deployment order and backward compatibility for high-availability systems.

### Separate Schema and Data
Keep schema changes separate from data migrations. This limits the blast radius of a failed migration and makes forward-only recovery easier.

### Careful Indexing
Create indexes on large tables carefully, using concurrent options when available.

### Descriptive Names
Use names that indicate what the migration does.

### Version Control
Commit migrations; never modify existing ones after deployment.

### Pre-deployment squash (2026-09-10) — one-time exception
Before anything was deployed, V1–V7 (which created the per-document artifact model, step checkpoints, pgvector and embeddings) were squashed into a single `V1__baseline.sql` holding only the tables that survived the wiki re-cut (Phase 3b; see `docs/wayfinder/tickets/12-existing-code-fate.md`). This was allowed only because no environment held data worth keeping. Every local database had to be dropped and recreated. The forward-only rule above applies without exception from the baseline on.
