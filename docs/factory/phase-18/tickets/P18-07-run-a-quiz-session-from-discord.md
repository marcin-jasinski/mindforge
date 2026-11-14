---
id: P18-07
title: Run a Quiz Session from Discord with /quiz start and /quiz answer over QuizService
status: done
plan_tasks: [18.2, 18.4]
decisions: [D05]
depends_on: [P18-05]
size: M
issue: 39
commit: b744a71
fix_rounds: 0
---
## Goal
`/quiz start kb:<knowledge base>` starts a Quiz Session over the whole knowledge base and replies with the first
question; `/quiz answer text:<answer>` grades the answer in the caller's current session and replies with the score,
feedback and the next question. Only `NextQuestion` and `Graded` content reaches Discord. User A cannot reach user B's
session (18.4).

## Context
- After P18-05: `DiscordCommand` plug-in, gate (allowlist -> rate limit -> ephemeral defer -> identity), `DiscordReply`
  seam, shared `kb` option and `resolveKnowledgeBase` (empty -> "Unknown knowledge base."). `/quiz` is one slash command
  with two subcommands, `start` (option `kb`, required, autocomplete) and `answer` (option `text`, required; no `kb`).
- `src/main/java/dev/mindforge/application/service/QuizService.java`: `QuizSession start(kbId, userId, scope)` (:84,
  sync), `Optional<NextQuestion> next(kbId, userId, sessionId)` (:177), `Graded answer(kbId, userId, sessionId, answer)`
  (:186); `record NextQuestion(int index, int total, String question)`, `record Graded(QuizEvaluation evaluation,
  boolean finished)`; `owned(...)` throws `NotOwnerException` for another user's session; `QuizFinishedException` exists.
- Working non-web precedent: `src/main/java/dev/mindforge/cli/QuizCommandRunner.java` (uses
  `new StudyScope.WholeKnowledgeBase()` at :101). `/quiz start` takes no scope option: use `WholeKnowledgeBase`.
- `AppProperties.Study` has `quiz-session-ttl` (`QUIZ_SESSION_TTL`, default PT2H). Caffeine is already a dependency.
- Security: never return `reference_answer`; never touch `QuizSession` fields in replies (it holds reference answers).
- Reply visibility (D14, applied in P18-05): every reply ephemeral; assert it for both subcommands.
- Test precedent for a real `QuizService`: `src/test/java/dev/mindforge/unit/application/QuizServiceTest.java:206`
  (real service, mocked `QuizSessionStore`, `StubAIGateway` from `src/test/java/dev/mindforge/support/`).

## Decisions to apply
**D05 — How a Discord quiz session is addressed.** Decision: the Discord adapter keeps the caller's current quiz in an
in-process Caffeine cache keyed by the resolved internal `userId`, value `(kbId, sessionId)`,
`expireAfterWrite(mindforge.study.quiz-session-ttl)`. `/quiz start` acknowledges with `deferReply(true)`, calls
`QuizService.start(kbId, userId, scope)` synchronously on the virtual thread, stores the entry (a new start replaces the
old one), calls `next(...)` and edits the deferred reply with the first question. `/quiz answer text:<...>` reads the
entry and calls `QuizService.answer(kbId, userId, sessionId, text)`. No domain port changes; the entry is lost on
restart and the user runs `/quiz start` again.
Implementation notes:
- Class `DiscordQuizSessions` in `dev.mindforge.discord`: `Cache<UUID, ActiveQuiz>` with `record ActiveQuiz(UUID kbId,
  UUID sessionId)`, key = internal userId from identity resolution, never the raw Discord id alone. Built in the Discord
  `@Configuration` from `mindforge.study.quiz-session-ttl`; methods `put(UUID userId, ActiveQuiz)`,
  `Optional<ActiveQuiz> current(UUID userId)`, `clear(UUID userId)`.
- `/quiz start`: allowlist -> `deferReply(true)` -> resolve user -> resolve kb (`requireOwner`) ->
  `quizService.start(kbId, userId, scope)` -> `put` -> `next(...)` -> edit reply with the rendered `NextQuestion`.
  `NotFoundException` from `start` (nothing to ask about) -> fixed reply. (The gate of P18-05 already does allowlist,
  rate limit, defer and identity.)
- `/quiz answer text:`: no entry -> fixed reply "No active quiz. Run /quiz start."; else `answer(...)`; render only
  `Graded.evaluation().score()/feedback()` and, if not `finished`, the next `NextQuestion`. On `finished` -> `clear`.
  `NotFoundException` / `NotOwnerException` -> `clear` and the same fixed reply (no existence oracle).
- Render only `NextQuestion` and `Graded` fields; never touch `QuizSession`.
- Do not use `begin`/`isPreparing` from Discord.
- Tests (plain Mockito, no Spring context): user B calling `answer` with A's sessionId through the real `QuizService`
  gets `NotOwnerException` mapped to the fixed reply; no-entry reply; second `/quiz start` replaces the entry; entry
  cleared on `finished`; reply text contains no reference answer.

## Acceptance criteria
- [x] AC1: `/quiz` is registered with subcommands `start` (required autocompleted `kb`) and `answer` (required `text`).
- [x] AC2: `/quiz start` on an owned knowledge base calls `QuizService.start(kbId, userId, new
  StudyScope.WholeKnowledgeBase())`, stores `(kbId, sessionId)` for the user, and replies with "Question i/n: ...".
- [x] AC3: A second `/quiz start` replaces the stored entry.
- [x] AC4: `/quiz answer` with an entry calls `answer(kbId, userId, sessionId, text)` and replies with score and
  feedback, plus the next question when not finished; on `finished` the entry is cleared.
- [x] AC5: `/quiz answer` with no entry replies "No active quiz. Run /quiz start." and calls no service.
- [x] AC6 (18.4): user B whose entry points at user A's session gets the "No active quiz. Run /quiz start." reply via
  the real `QuizService` ownership check (`NotOwnerException`), the entry is cleared, and nothing of A's session is shown.
- [x] AC7: No reply text contains the session's reference answer; `NotFoundException` from `start` and model-side
  failures map to fixed replies without exception messages.
- [x] AC8: Both subcommands reply ephemerally; `begin`/`isPreparing` are never called; no domain port changes.

## Test plan
- Unit (plain Mockito, no Spring context, AssertJ): `src/test/java/dev/mindforge/unit/discord/QuizCommandTest.java`
  (new) for AC1–AC5, AC7–AC8 with a mocked `QuizService` and a recording fake `DiscordReply`;
  AC6 with a real `QuizService` (built as in `QuizServiceTest.java:206`, mocked `QuizSessionStore` returning A's
  session, `StubAIGateway`) and a reference answer string asserted absent from replies.
- `src/test/java/dev/mindforge/unit/discord/DiscordQuizSessionsTest.java` (new): put/current/clear and replacement.

## Files likely touched
- `src/main/java/dev/mindforge/discord/QuizCommand.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordQuizSessions.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordConfig.java`
- tests listed above (new)

## Out of scope
- A per-user "current session" lookup in `QuizSessionStore` or any domain port change.
- Lesson or page scope options; flashcard review in Discord.

## Notes
- Decided: `start` with nothing to ask about replies "This knowledge base has nothing to quiz on yet." because the ticket asks only for a fixed reply without the exception message.
- Decided: model failures (`AIGatewayUnavailableException`, `DeadlineExceededException`, `ModelOutputException`) reuse `AskCommand.MODEL_UNAVAILABLE` and keep the entry on `/quiz answer`, because the session is still valid and the user can answer again.
- Decided: `QuizFinishedException` on `/quiz answer` is handled like `NotFoundException`/`NotOwnerException` (clear + "No active quiz. Run /quiz start.") because a finished session has nothing left to answer.
- Decided: a finished quiz appends "Quiz finished." after score and feedback, and the `text` option has max length 4000, the same as the web's `AnswerRequest`, because both match existing behaviour.
- Tester r1 AC map (QuizCommandTest unless noted): AC1 definition_hasAStartSubcommand...; AC2 start_onAnOwnedKnowledgeBase_...; AC3 aSecondStart_replacesTheStoredEntry (+DiscordQuizSessionsTest); AC4 answer_withAnEntry_... and answer_toTheLastQuestion_...; AC5 answer_withNoEntry_...; AC6 answer_whoseEntryPointsAtAnotherUsersSession_... (real QuizService); AC7 answer_throughTheRealService_repliesWithoutTheReferenceAnswer, start_withNothingToAskAbout_..., aModelFailureOnStart/Answer_...; AC8 throughTheGate_bothSubcommandsAreDeferredEphemerally...

### Deferred findings
- correctness/C1 minor 0.5 src/main/java/dev/mindforge/discord/QuizCommand.java:117: A `NotFoundException` from the follow-up `next(...)` call after a successful `answer(...)` is caught by the same catch as the grading call, so a graded and recorded answer is reported as "No active quiz. Run /quiz start." and its score and feedback are lost.
