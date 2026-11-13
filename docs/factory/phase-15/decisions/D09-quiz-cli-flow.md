---
id: D09
question: Q09
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q09.r1.tally.md]
adr:
---
## Decision
Use A: the quiz CLI starts a quiz with the synchronous `QuizService.start(kbId, userId, new StudyScope.WholeKnowledgeBase())`,
then loops `next` / read one stdin line / `answer`, as the web does after preparation. Whole knowledge base only; no
`--lesson` / `--page` options and do not move `StudyScopes`. Per answer, print the score (`n/5`) and the feedback; at
the end, print a summary. EOF on stdin ends the quiz cleanly with the summary.

## Why
- `start` and `begin` run the same `generate()`; `begin` only moves it to a background executor so an HTTP request does not wait (QuizService.java:84-106, javadoc 88-90). A terminal can block (virtual threads, D01 in-process run).
- `next` and `answer` are the same calls the web uses (QuizService.java:177-199), so the plan checklist "same `QuizService` path" holds (implementation-plan.md:1456).
- B adds a poll loop, sleeps, a timeout and late errors through `isPreparing().join()` (QuizService.java:114-135) for no user gain.
- No transaction wraps the model call in `start` (no `@Transactional` in the application layer; `generate` calls the model, then one insert at :173).
- Scope options are not in plan 15.2 (implementation-plan.md:1450-1451); `StudyScopes` is package-private in the API adapter. Minimal implementation.

## Rejected
- B: exact web sequence, but async state and polling in a batch process with no benefit.
- C: speculative scope options plus a move of an API-adapter helper; can be added on top of A later without a break.

## Dissent
None. Risk to watch: `start` returns a `QuizSession` that holds `referenceAnswer` and `groundingExcerpt`; the CLI must never print or log the session or its questions — only `NextQuestion` and `Graded`.

## Implementation notes
- `QuizCommandRunner` in `dev.mindforge.cli` (D03: command `quiz`, `@Profile("cli")` config, constructor injection). Resolve `--user` and call `KnowledgeBaseService.requireOwner(kbId, user.userId())` first (D04).
- `QuizSession s = quizService.start(kbId, userId, new StudyScope.WholeKnowledgeBase());` keep only `s.id()`.
- Loop: `Optional<NextQuestion> q = quizService.next(kbId, userId, s.id())`; empty ends the loop. Print `"Question %d/%d: %s"`. Read a line from a `BufferedReader` over stdin; `null` (EOF) ends the loop. `Graded g = quizService.answer(kbId, userId, s.id(), line)`; print `g.evaluation().score()` + `/5` and `g.evaluation().feedback()`; stop when `g.finished()`.
- Summary: questions answered, total and mean score.
- `NotFoundException` from `start` (no pages / no usable question): one line on stderr, non-zero exit.
- Tests (plain Mockito or `StubAIGateway`, no Spring context): full run of 5 answers prints scores, feedback and summary; EOF after first question ends cleanly with summary; `NotFoundException` gives stderr line and non-zero exit; stdout never contains the reference answer or grounding excerpt text of the stub session; `requireOwner` failure stops before `start`.
