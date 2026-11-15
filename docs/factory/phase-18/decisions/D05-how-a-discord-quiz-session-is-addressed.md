---
id: D05
question: Q05
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q05.r1.tally.md]
adr:
---
## Decision
Use A: the Discord adapter keeps the caller's current quiz in an in-process Caffeine cache keyed by the resolved
internal `userId`, value `(kbId, sessionId)`, `expireAfterWrite(mindforge.study.quiz-session-ttl)`. `/quiz start`
acknowledges with `deferReply(true)`, calls `QuizService.start(kbId, userId, scope)` synchronously on the virtual
thread, stores the entry (a new start replaces the old one), calls `next(...)` and edits the deferred reply with the
first question. `/quiz answer text:<...>` reads the entry and calls `QuizService.answer(kbId, userId, sessionId, text)`.
No domain port changes; the entry is lost on restart and the user runs `/quiz start` again.

## Why
- Ownership stays in the real service check: `next`/`answer` go through `owned(...)` (QuizService.java:177-187, 225-231).
- `QuizSessionStore` has no per-user lookup (QuizSessionStore.java); B changes a domain port for UI pointer state, and "latest" would also catch web sessions.
- Single live instance is the stated ceiling (architecture.md:260-261), so in-process state is consistent; Caffeine is already in the stack (tech-stack.md:84).
- `start` inside a deferred reply (3 s ack, 15 min edit window) avoids a `begin` + `isPreparing` polling path (QuizService.java:94-114).
- D03: `/quiz answer` takes no `kb`; it uses the kbId stored with the session. This decision supplies that storage.
- Unanimous, mean confidence 0.67, no hard objection.

## Rejected
- B: domain port + adapter + test for adapter pointer state; "latest" is ambiguous with web and preparing sessions.
- C: the user must type a session id; a short id needs a lookup, which is B again.

## Dissent
None. Risks to watch: a restart mid-quiz loses the pointer (the user sees "no active quiz"); a model call that runs
past the 15-min edit window. If more than one instance is ever run, reopen with B.

## Implementation notes
- Class `DiscordQuizSessions` in the Discord adapter package (per Q09): `Cache<UUID, ActiveQuiz>` with
  `record ActiveQuiz(UUID kbId, UUID sessionId)`, key = internal userId from identity resolution (D01), never the raw
  Discord id alone. Built in the Discord `@Configuration` from `mindforge.study.quiz-session-ttl`; methods
  `put(UUID userId, ActiveQuiz)`, `Optional<ActiveQuiz> current(UUID userId)`, `clear(UUID userId)`.
- `/quiz start`: D04 allowlist -> `deferReply(true)` -> resolve user (D01) -> resolve kb (D03 `requireOwner`) ->
  `quizService.start(kbId, userId, scope)` -> `put` -> `next(...)` -> `hook.editOriginal(render NextQuestion)`.
  `NotFoundException` from `start` (nothing to ask about) -> fixed reply.
- `/quiz answer text:`: no entry -> fixed reply "No active quiz. Run /quiz start."; else `answer(...)`; render only
  `Graded.evaluation().score()/feedback()` and, if not `finished`, the next `NextQuestion`. On `finished` -> `clear`.
  `NotFoundException` / `NotOwnerException` -> `clear` and the same fixed reply (no existence oracle).
- Render only `NextQuestion` and `Graded` fields; never touch `QuizSession` (holds reference answers).
- Do not use `begin`/`isPreparing` from Discord.
- Tests (plain Mockito, no Spring context): user B calling `answer` with A's sessionId through the real `QuizService`
  (in-memory store, `StubAIGateway`) gets NotOwnerException mapped to the fixed reply; no-entry reply; second
  `/quiz start` replaces the entry; entry cleared on `finished`; reply text contains no reference answer.
