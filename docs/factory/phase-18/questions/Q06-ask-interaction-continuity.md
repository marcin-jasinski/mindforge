---
id: Q06
status: decided
decision: D06
impact: low
blocks: [W06]
---
## Question
Does each `/ask` start a new Query interaction, or do a user's consecutive `/ask` commands continue one interaction
(so prior turns inform the answer)?

## Context
`QueryService.start(kbId, userId)` creates an `Interaction`; `ask(kbId, userId, interactionId, question)` uses its prior
turns (architecture.md § Query: "question + prior turns + rendered index"). The web keeps one interaction per chat
panel. Every turn is stored in `interactions`/`interaction_turns` and shows in `history(kbId, userId)` on the web.

## Known options
- A: New interaction per `/ask` — stateless adapter, no follow-up context, many one-turn interactions in the history.
- B: One interaction per (Discord user, knowledge base), held in an in-process Caffeine map with an idle timeout
  (e.g. 30 min); a `new` option or timeout starts a fresh one. Lost on restart.
- C: One interaction per Discord channel/thread per user.

## Constraints
- `requireSession` enforces that an interaction belongs to its user.
- No new port without a caller; an in-adapter map is in-process (single instance).
