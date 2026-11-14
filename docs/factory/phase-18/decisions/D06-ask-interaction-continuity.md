---
id: D06
question: Q06
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q06.r1.tally.md]
adr:
---
## Decision
Use A: each `/ask` starts a new Query interaction. The handler calls `QueryService.start(kbId, userId)`, then
`QueryService.ask(kbId, userId, interaction.id(), question)` once, and replies with the answer and its cited paths.
The Discord adapter keeps no interaction state.

## Why
- The `QueryService` API supports this directly (`QueryService.java:75,79`); `requireSession` (`:162`) passes because
  the same user creates and uses the interaction.
- No requirement asks for follow-up context in Discord (scope.md 18.2, implementation-plan.md:1581-1583, breakdown W06).
- A adds no in-process map, no timeout setting, no `new` option and no restart-dependent behaviour (minimal-implementation).
- Reversible: B can be added later in the adapter with no schema or port change.

## Rejected
- B: in-process Caffeine map, idle timeout and a `new` option for a need that no requirement states; behaviour changes on restart.
- C: needs channel/thread keys and the same in-process state as B, with more ways to get it wrong.

## Dissent
None (single ballot). Risk to watch: the web history fills with one-turn interactions from Discord; if users ask for
follow-ups, reopen with B.

## Implementation notes
- W06 handler: `var interaction = queryService.start(kbId, userId); var answer = queryService.ask(kbId, userId, interaction.id(), question);`
  with `kbId` and `userId` from identity and knowledge-base resolution (D01, Q03).
- Tests: the handler calls `start` then `ask` with the resolved user and knowledge base and the returned interaction id;
  two consecutive `/ask` calls create two interactions (`start` called twice); `NotOwnerException` from `ask` maps to a fixed error reply.
- Document in the Discord docs that each `/ask` is a one-turn interaction with no follow-up context.
