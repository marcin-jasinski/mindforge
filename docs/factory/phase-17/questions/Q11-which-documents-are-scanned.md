---
id: Q11
status: answered
impact: low
blocks: [W04]
answered_by: docs/project/implementation-plan.md#Phase 17 — Article Fetcher (Goal); src/main/java/dev/mindforge/domain/model/UploadSource.java (javadoc)
---
## Question
Which documents are scanned for URLs: are fetched articles and conversation turns scanned too (recursion)?

## Context
`UploadSource` has `API`, `CLI`, `FILE_WATCHER`, `CONVERSATION`, `ARTICLE`. A scanned article would fetch its own
links without bound.

## Known options
- Settled: the goal is "external articles referenced in uploaded documents". Scan only documents a user uploaded
  (`API`, `CLI`, `FILE_WATCHER`), and only when a new Document row was inserted (an identical upload returns the
  existing id and fetches nothing). `ARTICLE` (no recursion) and `CONVERSATION` (a chat turn, outside the lesson rule)
  are never scanned.

## Constraints
Untrusted content must not widen itself (T28 #1).
