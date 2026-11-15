---
id: P19-05
title: Run a Quiz Session through /mf-quiz start and /mf-quiz answer
status: ready
plan_tasks: [19.2, 19.3]
decisions: [D05, D07]
depends_on: [P19-04]
size: M
commit:
fix_rounds: 0
issue: 49
---
## Goal
`/mf-quiz start` starts a Quiz Session on the caller's only knowledge base and replies with the first question;
`/mf-quiz answer <text>` grades the answer to the current question and replies with the grade and the next question
(or "Quiz finished."). The current quiz is held in `SlackQuizSessions`, keyed by internal user id. All replies are
ephemeral; ownership is checked by `QuizService`.

## Context
- P19-03: gate, `SlackCommand`, `SlackCall`, ephemeral `SlackReply`. P19-04: `SlackKnowledgeBases` (the caller's only
  knowledge base, fixed refusal otherwise) and `SlackAskCommand` (holds the model-unavailable text pattern).
- Mirror, do not import: `src/main/java/dev/mindforge/discord/QuizCommand.java` and `DiscordQuizSessions.java`, tests
  `src/test/java/dev/mindforge/unit/discord/QuizCommandTest.java`, `DiscordQuizSessionsTest.java`. The real service
  flow is: `quizService.start(kbId, userId, new StudyScope.WholeKnowledgeBase()).sessionId()` (a `NotFoundException`
  → "This knowledge base has nothing to quiz on yet."), store `(kbId, sessionId)`, then `quizService.next(kbId, userId,
  sessionId)` → "Question i/n: …" or "Quiz finished."; answer: `quizService.answer(kbId, userId, sessionId, text)` →
  "Score: s/5\n<feedback>\n\n" + next question or "Quiz finished."; `NotFoundException | NotOwnerException |
  QuizFinishedException` → clear and reply the no-active-quiz text; model failures
  (`AIGatewayUnavailableException | DeadlineExceededException | ModelOutputException`) → "The model is not available
  now. Try again later."; answer length limit 4000 characters.
- Q12 (Phase 18 D05): the current quiz lives in an in-process Caffeine cache keyed by the resolved internal `userId`,
  value `(kbId, sessionId)`, `expireAfterWrite(mindforge.study.quiz-session-ttl)`; a new start replaces the old one;
  lost on restart; ownership stays in `QuizService`. Key is never the raw Slack id. `/mf-quiz answer` takes no
  knowledge base: it uses the one stored with the session (D03).
- Slack slash commands carry one free-text argument; Slack needs an ack within 3 s; the gate (P19-03) already acks and
  runs the command on a virtual thread, so the model-graded result is sent through `response_url`.
- Never put `reference_answer` or `grounding_context` in any reply (CLAUDE.md). Study data never enters a wiki page.

## Decisions to apply
**D05 — Decision:** Use A: `/mf-quiz` takes a text verb. `/mf-quiz start` starts a quiz (knowledge base chosen per
Q03) and replies with the first question; `/mf-quiz answer <text>` grades the answer to the current question and replies
with the grade and the next question. All replies are ephemeral. No buttons, no modals, no interaction handlers in
Phase 19.

**D05 — Implementation notes:**
- Slack quiz handler in `dev.mindforge.slack` (e.g. `SlackQuizCommand`): split the command text on the first
  whitespace; verb is case-insensitive `start` or `answer`.
- `start`: resolve kb per Q03, start the session (see Context for the real `QuizService` calls), store the session
  pointer keyed on the internal user id, reply with `NextQuestion` text only.
- `answer <text>`: no current quiz -> ephemeral hint to run `/mf-quiz start`; empty text -> ephemeral usage line; else
  `QuizService.answer(...)` then `next(...)`; reply with `Graded` text and the next question (or end-of-quiz).
- Empty or unknown verb -> ephemeral usage line; never start a quiz.
- Ack within 3 s; the model-graded result goes via `response_url`; no model call inside a transaction.
- Never put `reference_answer` or `grounding_context` in any reply.
- Tests: start, answer, empty text, unknown verb, `answer` with no session, empty answer text, user A cannot answer
  user B's session (ownership via `QuizService`), reply contains no reference answer.

**D07 — Decision (quiz sessions part; rate limiter is P19-03):** copy the helper into the Slack adapter as
`SlackQuizSessions` (keyed by internal user id `UUID`), with copied unit tests. Do not change any Discord class or
test. Do not import `dev.mindforge.discord` from `dev.mindforge.slack`.

**D07 — Implementation notes (quiz sessions):**
- `dev.mindforge.slack.SlackQuizSessions`: `public record ActiveQuiz(UUID kbId, UUID sessionId)`; constructor
  `SlackQuizSessions(Duration ttl)`; `put(UUID userId, ActiveQuiz)`, `Optional<ActiveQuiz> current(UUID userId)`,
  `clear(UUID userId)` — keyed by internal user id, not the Slack id.
- Build the bean in the Slack `@Configuration` from `mindforge.study.quiz-session-ttl`, as DiscordConfig does; no new
  settings.
- Tests: `src/test/java/dev/mindforge/unit/slack/SlackQuizSessionsTest`, copied from the Discord test. Cover: put
  replaces earlier quiz; clear removes it; current is empty for an unknown user.

## Acceptance criteria
- [ ] AC1: `SlackQuizSessions` stores one `ActiveQuiz` per internal user id; `put` replaces, `clear` removes; entries
  expire after the quiz-session TTL.
- [ ] AC2: `/mf-quiz start` (verb case-insensitive) resolves the only knowledge base via `SlackKnowledgeBases`, starts a
  whole-knowledge-base Quiz Session, stores `(kbId, sessionId)` and replies "Question i/n: …"; an empty knowledge base
  replies "This knowledge base has nothing to quiz on yet.".
- [ ] AC3: `/mf-quiz answer <text>` calls `QuizService.answer(kbId, userId, sessionId, text)` with the stored session
  and replies "Score: s/5", the feedback and the next question, or "Quiz finished." (and clears the stored quiz).
- [ ] AC4: `answer` with no current quiz replies with a hint to run `/mf-quiz start`; `answer` with empty text replies
  with a usage line; an empty or unknown verb replies with a usage line and never starts a quiz. Answers over 4000
  characters get the usage line.
- [ ] AC5: When user A's stored pointer names user B's session, the real `QuizService` ownership check refuses it; the
  reply is the no-active-quiz text and B's session is unchanged.
- [ ] AC6: A model failure replies "The model is not available now. Try again later."; no exception message is sent.
- [ ] AC7: No reply contains the reference answer or grounding context.
- [ ] AC8: `SlackConfig` registers `/mf-quiz` and builds `SlackQuizSessions`; `SlackConfigTest` still shows no Slack
  bean without tokens or in `cli`.

## Test plan
- AC1: unit `src/test/java/dev/mindforge/unit/slack/SlackQuizSessionsTest.java`.
- AC2–AC4, AC6, AC7: unit `src/test/java/dev/mindforge/unit/slack/SlackQuizCommandTest.java` (Mockito `QuizService`,
  `SlackKnowledgeBases`; recording reply double; `make*` factories for `NextQuestion`/`Graded`; assert reply text does
  not contain the reference answer of the fixture).
- AC5: unit test with the real `QuizService` over in-memory/Mockito ports as `QuizCommandTest` does for Discord (or an
  integration test under `src/test/java/dev/mindforge/integration/slack/` with Testcontainers and `StubAIGateway` if
  the Discord test uses one) — follow the Discord precedent.
- AC8: integration-tagged `SlackConfigTest`.

## Files likely touched
- `src/main/java/dev/mindforge/slack/SlackQuizSessions.java` (new)
- `src/main/java/dev/mindforge/slack/SlackQuizCommand.java` (new)
- `src/main/java/dev/mindforge/slack/SlackConfig.java`
- `src/test/java/dev/mindforge/unit/slack/SlackQuizSessionsTest.java` (new)
- `src/test/java/dev/mindforge/unit/slack/SlackQuizCommandTest.java` (new)
- `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`

## Out of scope
- Buttons, modals, `block_actions` or `view_submission` handlers.
- Scoped quizzes (lesson, page), flashcard review, reminders.
- Persisting the quiz pointer; a shared quiz-session class with Discord.

## Notes
