---
id: D14
question: Q14
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q14.r1.tally.md]
adr: 
---
## Decision
Use A (with A2's placement): in `RevertService.revert(kbId, runId)`, after `start(kbId, runId)` claims the lease and
inside the same transaction, read the run once with `runs.findById(kbId, runId).orElseThrow(() -> new NotFoundException("Run"))`.
A run that exists but is not a completed ingest or Lint run still gets `RevertNotAllowedException` (409) with the
current message. The exception rolls back the queued REVERT run, as the 409 path does today. Fix this under plan task
20.1 and record it in the P20-06 Notes as a gap fixed.

## Why
- Q10 is answered: a foreign id under one's own knowledge base gives 404, and it names revert (Q10:22, 26-27).
- Plan task 20.1 says "Fix any gaps found"; the ticket's test-only scope is narrower than the plan task it implements.
- Same pattern as `RunReportService.java:47`; `GlobalExceptionHandler` already maps `NotFoundException` to 404. No new concept.
- Reading after `start()` keeps the invariant in `RevertServiceTest.shouldRefuseWhileAnotherRunHoldsTheLease`
  (no `findById` while the lease is busy).
- `NotFoundException` is unchecked (`NotFoundException.java:4`), so `TransactionOperations` rolls back the REVERT row.
- Class `local`: the change brings one endpoint into line with an existing rule; it does not create a new contract.

## Rejected
- B: relaxes a security AC to fit the code and contradicts answered Q10.
- C: an existence pre-check in the controller is business logic in a controller (CLAUDE.md thin-controller rule).
- A before `start()`: breaks the lease-first invariant the unit test asserts and answers 404 instead of busy.

## Dissent
None on the choice. The architect placed the read before `start()`; that is overridden. Risk to watch: on a busy
knowledge base an unknown run id gets 409 busy, not 404. This is accepted: it leaks nothing and matches today's order.

## Implementation notes
- `src/main/java/dev/mindforge/application/service/RevertService.java`:
  - In `revert`, replace `if (!isRevertible(kbId, runId))` with
    `IngestRun target = runs.findById(kbId, runId).orElseThrow(() -> new NotFoundException("Run"));`
    then `if (!isRevertible(target)) throw new RevertNotAllowedException("Run " + runId + " is not a completed ingest or Lint run");`.
  - Change the helper to `private static boolean isRevertible(IngestRun run)` (kind != REVERT and status == COMPLETED).
    `isOffered` becomes `runs.findById(kbId, runId).filter(RevertService::isRevertible).isPresent() && !restorable(kbId, runId).isEmpty()`; its behaviour does not change.
  - Import `dev.mindforge.domain.model.NotFoundException`. Add `@throws NotFoundException when the knowledge base has no such run` to the Javadoc.
  - Leave `removeSupersession` unchanged (out of scope).
- `RunController.revert`: if it has `@ApiResponse` annotations, add `responseCode = "404", description = "No such run"`.
- Unit test in `src/test/java/dev/mindforge/unit/application/RevertServiceTest.java`:
  `shouldReportARunTheKnowledgeBaseDoesNotHaveAsNotFound` — `findById` returns `Optional.empty()`; assert `NotFoundException`;
  `verify(wiki, never()).revisionsByRun(any(), any())`; `verify(runs, never()).complete(...)`.
  Keep `shouldRefuseWhileAnotherRunHoldsTheLease` unchanged; it must still pass.
- `SecurityTest.aForeignRunIdOrPagePathUnderOnesOwnKnowledgeBaseIsNotFoundAndShowsNothingOfTheOwner` must pass as written (404, no data of A).
- Existing tests that assert 409 for an existing non-revertible run (e.g. `RevertAndLeaseTest.aRevertRunIsNotRevertibleAndTheRefusalLeavesNoRun`) must still pass.
