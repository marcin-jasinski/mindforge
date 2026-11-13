---
id: Q08
status: answered
impact: low
blocks: [W08]
answered_by: docs/project/implementation-plan.md#Phase 16 — Image Analysis (16.2); docs/adr/0013-ingest-typed-pipeline-no-agent-abstraction.md#Amendments
---
## Question
ADR 0013 fixes the ingest sequence `Preprocess → RelevanceGuard → Extract → …` and `CLAUDE.md` says "a new step is a
design change, not a plugin". Does adding image description contradict an accepted ADR (a blocker)?

## Context
The plan (the source of truth, v3.0 re-cut) adds the step explicitly in 16.2 — "before `RelevanceGuard`" — and ADR
0004 already defines `VISION` "(image analysis)". It is a design change the human has already made in the plan; it
adds a step at the front and changes nothing that ADR 0013 decided (single-shot calls, no Agent abstraction, closed
pipeline). ADR 0013 already carries an "Amendments" section for such refinements.

## Known options
- Settled: not a blocker. Record it as a dated amendment in ADR 0013 ("Describe (VISION) runs first, per IMAGE block")
  and update the ingest flow in `docs/project/architecture.md`. If Q03 extends the `AIGateway` port, the same
  amendment notes that the gateway gains an image-carrying single-shot completion.

## Constraints
House rule (`.claude/factory.md`): a decision contradicting an accepted ADR is a blocker; this one extends it as the
plan directs.
