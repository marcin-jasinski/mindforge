# Phase 15 — draft work items

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | `cli` profile boots without the web stack and without the run sweep that would touch a live server's runs; `application-cli.yml`; command selection skeleton (usage + non-zero exit on unknown command) in `dev.mindforge.cli` | 15.1, 15.2, checklist "both via `--spring.profiles.active=cli`" | M | — | Q01, Q02, Q03 |
| W02 | Console progress: the `cli` profile prints `RunProgress` (status, step, done/total) for the CLI's run; pipeline wiring takes the `ProgressNotifier` port where needed | 15.1 ("prints step progress") | S | W01 | Q01, Q05 |
| W03 | `mindforge-pipeline` runner: parse `--file`/`--kb` (+ flags per Q08), resolve acting user and ownership, MIME type, build `DocumentUpload`, call `IngestionService.ingest`, wait for terminal status, map outcomes to exit codes; unit test with mocked services | 15.1 | M | W01, W02 | Q01, Q04, Q06, Q07, Q08 |
| W04 | Integration test: `cli` profile context on Testcontainers PostgreSQL with `StubAIGateway` ingests a Markdown file end to end and ends `COMPLETED`; asserts no web server and no sweep of other runs | 15.1, checklist 1 | M | W03 | Q01, Q02 |
| W05 | `mindforge-quiz` runner: `--kb` (+ scope per Q09), start a quiz through `QuizService`, read answers from stdin, submit through `QuizService.answer`, print score/feedback only, summary at end; unit test with scripted stdin; integration test in the `cli` profile | 15.2, checklist 1–2 | M | W01 | Q03, Q04, Q09 |
| W06 | Docs: tick 15.1/15.2 and checklist in `implementation-plan.md`, roadmap, `deployment.md` (how to run both CLIs, interaction with a live server), CLAUDE.md "Project State"; INDEX only if a new doc is added | 15.1, 15.2 | S | W03, W05 | — |

Coverage: 15.1 → W01, W02, W03, W04, W06; 15.2 → W01, W05, W06. Checklist item 1 → W04, W05; item 2 → W05.
