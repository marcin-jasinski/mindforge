---
id: Q14
status: answered
impact: low
blocks: [W04]
answered_by: docs/wayfinder/tickets/18-lesson-and-path-identity.md#Answer (1, Exceptions); docs/wayfinder/tickets/07-idempotency-and-failure.md; docs/project/architecture.md#Idempotency & Reliability
---
## Question
What happens when an article's lesson id already exists in the knowledge base, or the same article content was already
ingested?

## Context
`IngestionService` throws `LessonAlreadyExistsException` when a lesson exists and the upload is not a new version, and
returns the existing id for identical content.

## Known options
- Settled: lesson id exists → skip and log; never a new version, never a 409 to anybody (T18 § 1 "Fetched articles …
  skipped and logged"; plan 17.2). Identical content → the existing document id, no new run (identical-upload rule).
  Both checks run under the knowledge-base row lock like any upload.

## Constraints
No auto-suffix of lesson ids (T18 "Why not auto-suffix").
