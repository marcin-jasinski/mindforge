---
id: Q05
status: decided
decision: D05
impact: medium
blocks: [W07]
---
## Question
How does `/quiz answer` find the caller's quiz session, and how does `/quiz start` handle the asynchronous question
generation?

## Context
Plan 18.2: `/quiz start`, `/quiz answer`; 18.4: "user A cannot reach user B's sessions". `QuizService.begin` returns a
session with no questions and generates in the background (`isPreparing`); `start` generates synchronously.
`next/answer(kbId, userId, sessionId, …)` reject another user's session through `owned(...)` (NotOwnerException).
`QuizSessionStore` has `insert`, `find(kbId, sessionId, now)`, `updateCursor`, `deleteExpired` — no lookup of a
user's current session. Sessions expire after `mindforge.study.quiz-session-ttl` (2 h). A Discord interaction must be
acknowledged within 3 s; a deferred reply can be edited for 15 min.

## Known options
- A: The adapter keeps "current session per (Discord user)" in an in-process Caffeine map with the session TTL;
  `/quiz answer text:<…>` uses it; a new `/quiz start` replaces it. Lost on restart (the user starts again). Uses
  `start` inside a deferred reply, or `begin` + poll `isPreparing`.
- B: Add `QuizSessionStore.findLatestFor(kbId, userId, now)` (port + adapter + test) and look the session up in the
  database; survives restart; touches a domain port.
- C: Expose the session id: `/quiz start` replies with a short id and `/quiz answer session:<id> text:<…>`. No state in
  the adapter; awkward to type; ownership still enforced by `QuizService`.

## Constraints
- Only `NextQuestion` and `Graded` content may reach Discord: never the reference answer or grounding
  (web-security.md; CLAUDE.md Security).
- The ownership test must go through the real `QuizService` ownership check, not an adapter-only check.
- Never introduce reactive types; JDA callbacks or blocking `complete()` on virtual threads.
- Single live instance (architecture.md § Idempotency & Reliability "Ceiling").
