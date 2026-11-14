---
id: Q08
status: decided
decision: D08
impact: medium
blocks: [W04, W05]
---
## Question
When one article cannot be ingested (policy violation, timeout, HTTP error, wrong type, too large, no title, lesson
exists, identical content), what happens and where is it visible?

## Context
- Plan 17.2 says only that an existing lesson is "skipped and logged" (T18).
- The parent upload has already been accepted; failing it because of a third-party site would be wrong. Whether the
  parent's run can record anything depends on Q01 (with option A/B the parent run has not started; with C it is
  already `COMPLETED` and its `failures` are written).
- `RunFailure` and run reports exist for run-level omissions; there is no per-document "notes" field.
- Logs must not carry secrets; failure text stored anywhere must be fixed text, not the remote body
  (`IngestRunFailedException.reasonFor` pattern).

## Known options
- A: Skip and `log.info`/`log.warn` with the URL and a fixed reason; nothing is stored. Parent unaffected.
- B: A, plus a count on the parent's run (`RunFailure.of("fetch", reason)`) — only possible if Q01 runs the fetch
  before the parent run completes (or amends a completed run, which runs never do).
- C: A, plus a Micrometer counter by reason (observability, Phase 14 style).

## Constraints
- Never a bare `RuntimeException` from business logic; typed exceptions (`error-handling.md`).
- A completed run is not rewritten (run lifecycle, T17).
- Fixed failure text, never remote content, in anything persisted.
