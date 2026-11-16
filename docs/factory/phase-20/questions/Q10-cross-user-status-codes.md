---
id: Q10
status: answered
impact: low
blocks: [W06]
answered_by: docs/standards/security/web-security.md#api-authorization; src/main/java/dev/mindforge/application/service/KnowledgeBaseService.java (requireOwner)
---
## Question
Which status must the 20.4 cross-user tests expect for pages, runs and export, including a run id from another
knowledge base requested under the caller's own knowledge base?

## Context
- Plan 20.4: "cross-user access to pages, runs, export → 403".
- Every knowledge-base route calls `knowledgeBases.requireOwner(kbId, userId)` first; a foreign `kbId` gives `403`
  (`ApiFlowTest.aUserCannotReachAnotherUsersKnowledgeBase`, `theOwnerDownloadsTheBundle...` already assert `403` for
  index, page, runs list, progress, export).
- A run or page id from knowledge base A asked for under the caller's own knowledge base B passes `requireOwner`, and
  the tenant-scoped ports (`findById(kbId, runId)`, `report(kbId, runId)`) find nothing → `404`, with no data from A.
  That is the tenancy rule (CLAUDE.md: tenancy is structural), not an ownership failure.

## Known options
- A: Foreign knowledge base in the path → `403`; foreign id under one's own knowledge base → `404` and no foreign data.

## Constraints
- `web-security.md` § API Authorization; CLAUDE.md § Architecture (every tenant-scoped port takes `kbId` first);
  review map "All controllers check ownership as their first statement" (do not re-litigate). Answer: A. New tests add
  the missing cases: run report by id and revert, page revisions, and the id-swap `404`.
