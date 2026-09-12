## Models

### Clear Naming
Use singular names for models and plural for tables (or follow framework conventions).

### Timestamps
Include created and updated timestamps for auditing and debugging.

### Database Constraints
Enforce data rules at the database level (NOT NULL, UNIQUE, foreign keys).

### Appropriate Types
Choose data types that match purpose and size requirements.

### Index Foreign Keys
Index foreign key columns and frequently queried fields.

### Multi-Layer Validation
Validate at both model and database levels for defense in depth.

### Clear Relationships
Define relationships with appropriate cascade behaviors and naming.

### Foreign Keys Inside One Cascade
A foreign key whose referencing and referenced rows are both removed by the same cascade (e.g. `page_sources.document_id`, both under `knowledge_bases`) is declared `NO ACTION DEFERRABLE INITIALLY DEFERRED`. It is then checked at commit, after the cascade has run, so a delete never depends on the order PostgreSQL fires cascades. Cover each such cascade with an integration test that deletes the root.

### Inserting Entities With Assigned Ids
Entities whose UUID is assigned by the application implement `Persistable<UUID>` with `isNew()` true until persisted, so Spring Data's `save()` issues one `INSERT` instead of `merge`'s select-then-insert.

### Practical Normalization
Balance normalization with query performance needs.

### MapStruct for Entity↔Domain Mapping
Use `@Mapper(componentModel = "spring")` interfaces in `infrastructure.persistence.mapper` for all entity↔domain translation. Never write manual `toEntity`/`toDomain`/`populateEntity` methods in adapter classes — the mapper is the single place field-by-field translation lives. Let Spring inject mappers via constructor; never call `Mappers.getMapper()`.
