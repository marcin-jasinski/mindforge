---
id: Q08
status: decided
decision: D08
impact: medium
blocks: [W03]
---
## Question
What does the pipeline CLI do on each outcome — and does it accept `--lesson-id` / `--new-version` — and with which
exit codes?

## Context
- Plan 15.1 lists only `--file` and `--kb`.
- `IngestionService.ingest` outcomes: new document + queued run; identical upload → returns the existing document id
  and queues nothing; `LessonAlreadyExistsException` (web 409: "send newVersion or another lessonId");
  `UnknownLessonException`, `LessonIdentityException`, `UploadRejectedException` (web 422).
- A run ends `COMPLETED` (maybe with per-page failures or `supersession_skipped`) or `FAILED`; the sweep may re-queue.
- Without `--new-version`, a second version of a lesson can never be ingested from the CLI.
- Scripted use (plan Goal: "scripted ingestion") needs a stable exit code.

## Known options
- A: Only `--file`/`--kb`; wait for the terminal status; exit 0 on `COMPLETED`, non-zero on `FAILED` or a refused
  upload; identical upload prints the existing document and its latest run state, exit 0.
- B: A plus `--lesson-id <id>` and `--new-version`, mapped to `DocumentUpload.lessonId/newVersion` (parity with the web
  upload form).
- C: Do not wait: print the document id and run id after queueing, exit 0 (conflicts with "prints step progress").

## Constraints
- Thin adapter: outcome mapping only, no business logic (hexagonal.md).
- Typed domain exceptions; clear user messages (docs/standards/global/error-handling.md).
