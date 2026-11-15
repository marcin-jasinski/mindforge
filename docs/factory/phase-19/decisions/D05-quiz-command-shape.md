---
id: D05
question: Q05
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q05.r1.tally.md]
adr: 
---
## Decision
Use A: `/mf-quiz` takes a text verb. `/mf-quiz start` starts a quiz (knowledge base chosen per Q03) and replies with the
first question; `/mf-quiz answer <text>` grades the answer to the current question and replies with the grade and the
next question. All replies are ephemeral. No buttons, no modals, no interaction handlers in Phase 19.

## Why
- Mirrors Discord `/quiz start` / `/quiz answer` (discord/QuizCommand.java:35-37), so both bots use the same
  `QuizService.next/answer(kbId, userId, sessionId, ...)` path and the same ownership checks (QuizCommand.java:111,128).
- Plan 19.2 names one command `/mf-quiz` and no interaction handlers (implementation-plan.md:1652-1653).
- An explicit verb removes the ambiguity of C (an answer "start" or empty text).
- B adds `block_actions` + `view_submission` handlers, each a new ownership surface; it can be added later without
  breaking A.
- Ephemeral per ADR 0032:28.

## Rejected
- B: two extra interaction handlers and modal state for a usability gain; out of plan 19.2 scope.
- C: empty vs. text intent is ambiguous; a stray empty command can start a new quiz over the current one.

## Dissent
None. Risk to watch: long or multi-line answers in the slash composer; if users complain, add a modal (B) on top of A.

## Implementation notes
- Slack quiz handler in `dev.mindforge.slack` (e.g. `SlackQuizCommand`): split the command text on the first
  whitespace; verb is case-insensitive `start` or `answer`.
- `start`: resolve kb per Q03, call `QuizService.next(kbId, userId, sessionId)`, store the session pointer per D05/Q12
  keyed on the internal user id, reply with `NextQuestion` text only.
- `answer <text>`: no current quiz -> ephemeral hint to run `/mf-quiz start`; empty text -> ephemeral usage line; else
  `QuizService.answer(...)` then `next(...)`; reply with `Graded` text and the next question (or end-of-quiz).
- Empty or unknown verb -> ephemeral usage line; never start a quiz.
- Ack within 3 s; the model-graded result goes via `response_url`; no model call inside a transaction.
- Never put `reference_answer` or `grounding_context` in any reply.
- Tests: start, answer, empty text, unknown verb, `answer` with no session, empty answer text, user A cannot answer
  user B's session (ownership via `QuizService`), reply contains no reference answer.
