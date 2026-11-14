---
id: Q15
status: answered
impact: low
blocks: [W08]
answered_by: src/main/java/dev/mindforge/application/service/DocumentUpload.java; src/main/java/dev/mindforge/infrastructure/config/AppProperties.java#Upload ("Largest document accepted, on every upload channel"); docs/factory/phase-15/decisions/D06-cli-upload-source.md; docs/project/implementation-plan.md#Phase 15 (15.1 As built)
---
## Question
Which options does `/upload` take, which limits apply, and what source does the document record?

## Context
`DocumentUpload(kbId, uploadedBy, source, filename, mimeType, content, lessonId, newVersion)`; the web takes
`lessonId` and `newVersion`; the CLI takes `--lesson-id` and `--new-version`. `UploadSanitizer` applies MIME and size
limits on every channel. `IngestionService.ingest` throws `LessonAlreadyExistsException` (web 409),
`UploadRejectedException`, `LessonIdentityException`, `UnknownLessonException` (web 422).

## Known options
- Settled: `/upload file:<attachment> [lesson_id] [new_version]`, mirroring web and CLI; `UploadSource.DISCORD` added
  with this producer (D06); filename and MIME from the attachment, admitted by the same `UploadPolicy`; the declared
  attachment size is checked against `mindforge.upload.max-size` before download (Q07). Exceptions map to fixed reply
  texts equivalent to the web's 409/422 messages ("lesson exists; send new_version or another lesson_id"). An identical
  upload replies with the existing document as the web does. The reply says the run is queued; it does not wait for the
  run.

## Constraints
- No new upload rule for Discord; the same `IngestionService.ingest` path as the web (checklist "Commands delegate to the
  same application services as the web UI").
