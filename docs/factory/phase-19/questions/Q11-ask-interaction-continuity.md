---
id: Q11
status: answered
impact: low
blocks: [W05]
answered_by: docs/factory/phase-18/decisions/D06-ask-interaction-continuity.md#Decision
---
## Question
Does each `/mf-ask` start a new Query interaction, or continue an earlier one?

## Context
D06: each `/ask` calls `QueryService.start(kbId, userId)` then `ask(...)` once; the adapter keeps no interaction
state. The same reasons apply to Slack (no stated follow-up requirement; reversible).

## Known options
- A (settled): one-turn interaction per `/mf-ask`; reply with the answer and its cited page paths.

## Constraints
`NotOwnerException` and other refusals map to fixed texts; documented as one-turn in deployment.md.
