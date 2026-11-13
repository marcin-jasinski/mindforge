---
id: D06
question: Q06
status: decided
method: judge
class: local
votes: { pragmatist: C }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q06.r1.tally.md]
adr:
---
## Decision
Use C: add a `CLI` constant to `dev.mindforge.domain.model.UploadSource` and record it on every document the CLI upload command ingests. Do not use or remove `FILE_WATCHER` in this phase.

## Why
- `upload_source` is `VARCHAR(50)` with no CHECK constraint (V1__baseline.sql:37), and the mapper uses `UploadSource.valueOf` (DocumentEntityMapper.java:27): no migration, no mapper change.
- Only `CONVERSATION` and `ARTICLE` branch run behaviour (IngestPipeline.java:157-158, IngestionService.java:155); `CLI` behaves exactly like `API`, including content-hash deduplication.
- The CLI (W03) is a real producer, so the "no value without a producer" rule is met; the column keeps the true channel.
- The value is not exposed in API DTOs or the frontend, so no public contract changes.

## Rejected
- A (`API`): correct behaviour but loses the channel; the cost of C is one enum constant.
- B (`FILE_WATCHER`): records a false channel and gives meaning to an orphan value.

## Dissent
None (single ballot). Risk to watch: any future code that branches on `== UploadSource.API` would silently exclude CLI documents; prefer branching on the special values (`CONVERSATION`, `ARTICLE`).

## Implementation notes
- `UploadSource.java`: add `CLI` (place after `API`); update the Javadoc if it lists channels.
- CLI upload command (`dev.mindforge.cli`): build `new DocumentUpload(kbId, userId, UploadSource.CLI, ...)` the same way `DocumentController.java:60` does with `API`.
- Tests: unit test that the CLI command passes `UploadSource.CLI` to the ingestion service; persistence round-trip (Testcontainers) that a `CLI` document saves and loads; a duplicate CLI upload of the same content in one KB is deduplicated as for `API`.
- Phases 18–19 (bots) should follow the same pattern: one constant per real channel, added with its producer.
