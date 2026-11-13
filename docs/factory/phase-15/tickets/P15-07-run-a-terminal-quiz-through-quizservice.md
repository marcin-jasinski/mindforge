---
id: P15-07
title: Run an interactive terminal quiz over a Knowledge Base through QuizService
status: ready
plan_tasks: [15.2]
decisions: [D09, D04]
depends_on: [P15-03, P15-04]
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
`--spring.profiles.active=cli quiz --kb <id> --user <email>` resolves the user, checks ownership, starts a Quiz Session
over the whole Knowledge Base with the synchronous `QuizService.start`, then asks each question on stdout, reads one
answer line from stdin, grades it through `QuizService.answer` (the same path as the web UI), prints the score and
feedback, and prints a summary at the end. EOF on stdin ends the quiz cleanly with the summary. The reference answer and
the grounding excerpt are never printed or logged.

## Context
- After P15-03: `CliCommand` (`QUIZ = "quiz"`), `CliExit { OK(0), RUN_FAILED(1), REFUSED(2) }`, `CliUsageRunner`,
  `CliExitOnReady` (disabled by `mindforge.cli.exit-when-done=false` in tests), `CliConfig` (`@Profile("cli")`). Runners
  implement `ApplicationRunner` + `ExitCodeGenerator`, never call `System.exit`, never throw for an expected outcome.
- After P15-04: `AccountService.byEmail(String)` (throws `NotFoundException("User")`).
- `application/service/QuizService.java`: `start(kbId, userId, StudyScope)` (sync; throws `NotFoundException` when the
  scope has no page or the model wrote no usable question), `next(kbId, userId, sessionId): Optional<NextQuestion>`
  (`record NextQuestion(int index, int total, String question)`, index 1-based), `answer(kbId, userId, sessionId,
  answer): Graded` (`record Graded(QuizEvaluation evaluation, boolean finished)`; records the quiz score). `begin` /
  `isPreparing` are the async web path; do not use them.
- `domain/model/QuizEvaluation.java`: `record QuizEvaluation(int score, String feedback)` (score 0–5).
  `domain/model/QuizSession.java` holds questions with `referenceAnswer` and `groundingExcerpt`.
- `StudyScope` is a sealed interface in `dev.mindforge.domain`; `StudyScope.WholeKnowledgeBase`.
- `KnowledgeBaseService.requireOwner(kbId, userId)`; `NotOwnerException`, `NotFoundException` in `domain/model/`.
- ADR-0019; CLAUDE.md Security: never expose `reference_answer` / `grounding_context`; study data never leaves the study
  path.

## Decisions to apply
**D09 — Decision:** The quiz CLI starts a quiz with the synchronous `QuizService.start(kbId, userId, new
StudyScope.WholeKnowledgeBase())`, then loops `next` / read one stdin line / `answer`, as the web does after
preparation. Whole knowledge base only; no `--lesson` / `--page` options and do not move `StudyScopes`. Per answer,
print the score (`n/5`) and the feedback; at the end, print a summary. EOF on stdin ends the quiz cleanly with the
summary. Risk: `start` returns a `QuizSession` that holds `referenceAnswer` and `groundingExcerpt`; the CLI must never
print or log the session or its questions — only `NextQuestion` and `Graded`.

**D09 — Implementation notes:**
- `QuizCommandRunner` in `dev.mindforge.cli` (command `quiz`, `@Profile("cli")` config, constructor injection).
  Resolve `--user` and call `KnowledgeBaseService.requireOwner(kbId, user.userId())` first (D04).
- `QuizSession s = quizService.start(kbId, userId, new StudyScope.WholeKnowledgeBase());` keep only `s.id()`.
- Loop: `Optional<NextQuestion> q = quizService.next(kbId, userId, s.id())`; empty ends the loop. Print
  `"Question %d/%d: %s"`. Read a line from a `BufferedReader` over stdin; `null` (EOF) ends the loop.
  `Graded g = quizService.answer(kbId, userId, s.id(), line)`; print `g.evaluation().score()` + `/5` and
  `g.evaluation().feedback()`; stop when `g.finished()`.
- Summary: questions answered, total and mean score.
- `NotFoundException` from `start` (no pages / no usable question): one line on stderr, non-zero exit.
- Tests (plain Mockito or `StubAIGateway`, no Spring context): full run of 5 answers prints scores, feedback and
  summary; EOF after first question ends cleanly with summary; `NotFoundException` gives stderr line and non-zero exit;
  stdout never contains the reference answer or grounding excerpt text of the stub session; `requireOwner` failure
  stops before `start`.

**D04 — Decision (quiz part):** `quiz` takes a required `--user <email>`, resolved with `AccountService.byEmail`; the
runner calls `requireOwner(kbId, user.userId())` before any work and passes `user.userId()` to every `QuizService` call,
so `owned` and `recordQuizScore` use it. Missing `--user`, unknown email, unknown KB or `NotOwnerException`: one line to
stderr, exit 2, no quiz session. Message for KB failures: "knowledge base not found or not owned by <email>". No
password; no fallback to the KB owner.

## Exit codes (D08 contract from P15-03)
0 = quiz ended (all answered or EOF); 2 = usage error (`quiz` without `--kb`/`--user`, or `--kb` not a UUID), unknown
user, KB not found / not owned; `NotFoundException` from `start` (nothing to ask) -> 2 (input refused, no session
created); any other unexpected exception -> 1 (logged with `log`).

## Acceptance criteria
- [ ] AC1: `QuizCommandRunner` does nothing (exit 0, no service call) when the command is not `quiz`.
- [ ] AC2: `quiz` without `--kb` or `--user`, or with a non-UUID `--kb` -> usage on stderr, exit 2, no service call.
- [ ] AC3: Unknown email or KB not owned -> one stderr line, exit 2, `QuizService.start` never called.
- [ ] AC4: Happy path calls `start(kbId, resolvedUserId, StudyScope.WholeKnowledgeBase)` and passes `resolvedUserId` to
      every `next` and `answer` call.
- [ ] AC5: With 5 questions and 5 input lines, stdout shows `Question 1/5: ...` to `Question 5/5: ...`, each score as
      `n/5` with its feedback, and a summary with answered count, total and mean score; exit 0.
- [ ] AC6: EOF after the first question -> exactly one `answer` call, summary printed, exit 0.
- [ ] AC7: `NotFoundException` from `start` -> one stderr line, non-zero exit (2), no `next`/`answer` call.
- [ ] AC8: stdout and stderr never contain the stub session's reference answer or grounding excerpt text.
- [ ] AC9: In a `cli` context (Testcontainers, `StubAIGateway`, `mindforge.cli.exit-when-done=false`), running the
      `QuizCommandRunner` with `quiz --kb <id> --user <owner email>` over a KB with at least one wiki page and scripted
      answers prints at least one graded answer and a summary, exits 0, and the quiz score is recorded for that user's
      page through the normal study store.

## Test plan
- AC1–AC8: unit, `src/test/java/dev/mindforge/unit/cli/QuizCommandRunnerTest.java` (new): Mockito mocks of
  `AccountService`, `KnowledgeBaseService`, `QuizService`; stdin from a `ByteArrayInputStream`, stdout/stderr to
  `ByteArrayOutputStream` `PrintStream`s, all passed through the constructor (the bean method passes `System.in`,
  `System.out`, `System.err`); `make*` factories for `QuizSession` with a known reference answer and excerpt.
- AC9: integration, `src/test/java/dev/mindforge/integration/cli/QuizCommandIntegrationTest.java` (new),
  `@Tag("integration")`, `@ActiveProfiles("cli")`, extends `TestContainerBase`; build the runner from context beans with
  scripted streams; seed pages with `TestFixtures`/the wiki store; check the score through `StudyProgressStore`.

## Files likely touched
- `src/main/java/dev/mindforge/cli/QuizCommandRunner.java` (new)
- `src/main/java/dev/mindforge/cli/CliConfig.java`
- `src/test/java/dev/mindforge/unit/cli/QuizCommandRunnerTest.java` (new)
- `src/test/java/dev/mindforge/integration/cli/QuizCommandIntegrationTest.java` (new)

## Out of scope
- `QuizService.begin` / `isPreparing` polling; `--lesson` / `--page` scope options; moving `api/controller/StudyScopes`.
- Any change to `QuizService` or `QuizEvaluator`.
- Printing or logging `QuizSession`, `QuizQuestion`, reference answers or grounding excerpts.
- Flashcards, Query or other CLI commands.

## Notes
