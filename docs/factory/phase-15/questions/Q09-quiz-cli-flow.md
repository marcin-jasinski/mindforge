---
id: Q09
status: decided
decision: D09
impact: medium
blocks: [W05]
---
## Question
Which `QuizService` entry does the quiz CLI use to start (`begin` + `isPreparing` polling like the web UI, or the
synchronous `start`), which study scope options does it take, and what does it print per answer?

## Context
- Plan 15.2: "Interactive terminal quiz through the same `QuizService` as the web UI"; checklist: "submits answers
  through the same `QuizService` path as the web UI".
- Web path (`QuizController`): `begin` (questions written in the background), poll `isPreparing`, then `next` and
  `answer`. `QuizService.start` is synchronous and today has no production caller (unit tests only).
- Scope: the web maps `lessonId` / `pageId` to `StudyScope` in `api/controller/StudyScopes` (package-private, API
  adapter). A CLI reuse needs it moved or duplicated.
- `QuizService.Graded(QuizEvaluation(score, feedback), finished)` — no reference answer.

## Known options
- A: `start` (sync; simplest in a terminal; gives `start` a production caller); answers through `answer` as the web.
  Scope: whole knowledge base only.
- B: `begin` + poll `isPreparing` — the exact web sequence. Scope: whole knowledge base only.
- C: A or B plus `--lesson <id>` / `--page <uuid>` scope options, with `StudyScopes` moved somewhere both adapters can
  use.
- Per answer, print score (0–5) and feedback; at the end print a summary. EOF on stdin ends the quiz.

## Constraints
- Never output `reference_answer` or `grounding_context` (CLAUDE.md Security; web-security.md).
- Same `QuizService.answer` path as the web (plan checklist).
- Thin adapter; minimal implementation.
