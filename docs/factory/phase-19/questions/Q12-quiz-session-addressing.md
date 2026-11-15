---
id: Q12
status: answered
impact: low
blocks: [W06]
answered_by: docs/factory/phase-18/decisions/D05-how-a-discord-quiz-session-is-addressed.md#Decision
---
## Question
How does an answer find the caller's current Quiz Session?

## Context
D05: an in-process Caffeine cache keyed by the resolved internal `userId`, value `(kbId, sessionId)`,
`expireAfterWrite(mindforge.study.quiz-session-ttl)`; a new start replaces the old one; lost on restart; ownership
stays in `QuizService.owned(...)`. Single live instance (architecture.md).

## Known options
- A (settled): the same for Slack (the class copy-or-share choice is Q07).

## Constraints
Key is the internal user id, never the raw Slack id alone; `/mf-quiz` answers take no knowledge base (D03/ADR 0030
analogue: the session stores it).
