---
id: Q06
status: decided
decision: D06
impact: low
blocks: [W03]
---
## Question
Which `UploadSource` does a CLI upload record: `API`, the unused `FILE_WATCHER`, or a new `CLI` value?

## Context
- `domain/model/UploadSource.java`: `API, FILE_WATCHER, CONVERSATION, ARTICLE`. `CONVERSATION` and `ARTICLE` change run
  behaviour; `API` and `FILE_WATCHER` do not. `FILE_WATCHER` has no producer (no plan task).
- `documents.upload_source` is `VARCHAR(50)` with no CHECK constraint (`V1__baseline.sql`), so a new value needs no
  migration.
- Phases 18–19 (bots) will face the same choice.

## Known options
- A: `API` — the CLI is one more channel into the same service.
- B: `FILE_WATCHER` — reuse the unused value for local-file ingest.
- C: Add `CLI` — records the real channel; one enum constant.

## Constraints
- Domain enum stays JDK-only; minimal implementation (no value without a producer).
