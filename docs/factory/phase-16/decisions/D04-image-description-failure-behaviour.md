---
id: D04
question: Q04
status: decided
method: unanimous
class: local
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q04.r1.tally.md]
adr: 
---
## Decision
Use B: when the vision model returns blank or unusable output for one image, drop that IMAGE block, record a
`RunFailure` with step `describe` on the run, and continue with the other blocks in document order. Infrastructure
errors are not per-image: let `AIGatewayUnavailableException` (circuit open, provider unreachable) and
`DeadlineExceededException` propagate so the whole run fails as retryable. If the document had IMAGE blocks, every
one was dropped, and no TEXT block remains, fail the run with a fixed, retryable reason, not "the document has no text".

## Why
- Partial success is already legal and counted (architecture.md:218; `RunFailure.ofItem`/`dropped`, RunFailure.java:22-29).
- ai_agents.md:91 allows an omission when it is counted on the run; D01 requires "a loud failure or a counted omission, never silent".
- A would let one blank logo image fail every retry of the document, because each retry describes every image again (D01).
- A transient outage must not drop images for good: the existing `run()` catch already makes those exceptions a
  retryable failure with their fixed domain message (IngestPipeline.java:124-138; `reasonFor`).
- Without the all-dropped guard an image-only PDF falls to the non-retryable "the document has no text" (IngestPipeline.java:178-181).

## Rejected
- A: one deterministic bad image blocks the document permanently; retries re-pay all vision calls.
- C: code-written placeholder text enters Extract input; an omission is counted, not papered over (ai_agents.md:91).

## Dissent
None on the choice. Risk to watch: if blank output turns out to be mostly transient, images are lost on runs that
complete; the `describe` RunFailure lines on the run report make this visible.

## Implementation notes
- Blank/unusable = completion text null or blank after trim. `ImageDescriber` (agent) does not throw for it; it
  returns an empty `Optional<String>` (or a result record) per image, and lets gateway/deadline exceptions propagate
  unchanged. Do not catch `RuntimeException` broadly in the describe step.
- `IngestPipeline.generateAndCommit` (describe step, before `Preprocessor.clean`): per dropped image,
  `state.addFailure(RunFailure.ofItem("describe", "block " + position, null, "no usable description"))`. Fixed text
  only, never provider output or messages. Remaining blocks keep their order (D09).
- After describing: if at least one IMAGE block existed, all were dropped, and no non-blank TEXT block remains, throw
  `new IngestRunFailedException("no image could be described", true)`.
- `state.version(ImageDescriber...)` is still recorded when any IMAGE block existed (D01), even if all were dropped.
- Frontend: add `describe: 'image description'` to `FAILURE_STEPS` in `frontend/src/app/shared/labels.ts`, with a
  spec case for `failureLine`.
- Tests (`StubAIGateway` with VISION, plain Mockito): blank output for one of two images -> run completes, one
  `describe` RunFailure with the block position, the other description reaches Extract; gateway unavailable ->
  run FAILED retryable, no `describe` drop recorded; deadline exceeded -> run FAILED retryable; image-only document
  with all images blank -> FAILED retryable with "no image could be described"; text plus all images blank -> run
  continues on text with the drops recorded.
