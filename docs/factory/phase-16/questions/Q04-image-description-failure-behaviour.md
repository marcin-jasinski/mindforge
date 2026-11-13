---
id: Q04
status: decided
decision: D04
impact: medium
blocks: [W05, W06]
---
## Question
When the vision call for one image fails (provider unavailable, deadline exceeded, empty or unusable output), does
the whole run fail, or is that image dropped and the omission recorded on the run?

## Context
- Plan 16.2 says nothing about failure.
- `IngestPipeline`: model failures before commit 1 fail the run (`IngestRunFailedException`, retryable or not;
  unexpected `RuntimeException` → retryable failure). Partial success is legal for page writes: "A run fails only when
  no page task succeeded" (`docs/project/architecture.md` § Idempotency & Reliability), and dropped items are recorded
  as `RunFailure` (`src/main/java/dev/mindforge/domain/model/RunFailure.java`, e.g. `RunFailure.ofItem`/`dropped`).
- `ai_agents.md`: "Fail loudly, never truncate … Where an input must be bounded and omission is acceptable …, the
  omission is counted on the run."
- An image-only PDF whose images all fail would then reach "the document has no text" (not retryable) unless the
  failure is retryable.
- A new failure step name (e.g. `describe`) shows raw in the UI unless added to `FAILURE_STEPS` in
  `frontend/src/app/shared/labels.ts`.

## Known options
- A: Any image failure fails the run (retryable for gateway/deadline errors). Simple, loud; one bad image blocks the
  document.
- B: Drop the failed image, record `RunFailure` (step `describe`, item = block position or count), continue. The run
  still completes with text. Gateway-unavailable (circuit open) could still fail the run as retryable.
- C: Replace the failed image with a fixed placeholder text. Puts code-written text into Extract input; weak.

## Constraints
- Never throw bare `RuntimeException`; domain exceptions (`CLAUDE.md`; `docs/standards/global/error-handling.md`).
- A run records fixed text, never provider messages (`AIGatewayAdapter`; `IngestRunFailedException.reasonFor`).
- Omissions are counted on the run (`ai_agents.md`).
