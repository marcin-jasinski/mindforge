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
