---
id: Q14
status: decided
decision: D14
impact: high
blocks: [P20-06]
raised_by: implementer (P20-06, NEEDS_DECISION)
---
## Question
User B calls `POST /api/v1/.../{ownKbId}/runs/{foreignRunId}/revert` with a run id that belongs to user A's knowledge base.
Today this returns `409 REVERT_NOT_ALLOWED` ("Run <id> is not a completed ingest or Lint run"). P20-06 AC4 and the
answered question Q10 (`questions/Q10-cross-user-status-codes.md`) expect `404` for a foreign id under one's own knowledge base.
The report and revisions endpoints already return 404 for this case. Should the factory change production code so revert
returns 404, or relax AC4 for revert?

## Context
- `src/main/java/dev/mindforge/application/service/RevertService.java` — `revert(kbId, runId)`; `isRevertible` treats a run that
  `runs.findById(kbId, runId)` does not find the same as a run that exists but is not revertible.
- Ticket: `tickets/P20-06-security-integration-tests.md` (its scope says test-only; its Notes record the gap).
- Test: `src/test/java/dev/mindforge/integration/api/SecurityTest.java`, method
  `aForeignRunIdOrPagePathUnderOnesOwnKnowledgeBaseIsNotFoundAndShowsNothingOfTheOwner` (fails at the revert assertion).
- The 409 body leaks nothing of A: its detail repeats only the id B sent.
- Q10 answered: cross-user access to a foreign kb → 403; a foreign resource id under one's own kb → 404.
- `docs/standards/global/error-handling.md`, `docs/standards/backend/api.md`.

## Options
- A: Small production change in `RevertService.revert`: throw `NotFoundException("Run")` when `runs.findById(kbId, runId)`
  is empty, before the run starts; an existing non-revertible run still gets 409. Add a unit test. User-visible status
  change (409 → 404) for an unknown run id.
- B: Keep production code; change AC4 for revert to accept `409 REVERT_NOT_ALLOWED` and only prove the body shows nothing of A.

## Constraints
- Minimal implementation (`docs/standards/global/minimal-implementation.md`); CLAUDE.md non-negotiables; the plan task 20.1
  says "Fix any gaps found" in the security checklist pass.
