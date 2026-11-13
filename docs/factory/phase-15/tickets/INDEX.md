# Phase 15 tickets — execution order

| Id | Title | Depends on | Size | Status |
|---|---|---|---|---|
| [P15-01](P15-01-boot-cli-profile-without-web-stack-or-sweep.md) | Boot the cli profile without the web stack and without the run sweep | — | M | ready |
| [P15-02](P15-02-print-run-progress-to-the-console.md) | Print run progress to the console in the cli profile | P15-01 | S | ready |
| [P15-03](P15-03-select-the-cli-command-and-exit-with-a-code.md) | Select the CLI command from the first argument and exit with a stable code | P15-02 | M | ready |
| [P15-04](P15-04-resolve-cli-user-by-email-and-cli-upload-source.md) | Resolve a user by email and add the CLI upload source | — | S | ready |
| [P15-05](P15-05-admit-a-file-through-the-pipeline-command.md) | Admit a file into a knowledge base through the pipeline command as a named user | P15-03, P15-04 | M | ready |
| [P15-06](P15-06-run-the-ingest-in-process-and-report-its-outcome.md) | Run the pipeline command's Ingest Run in process and exit with its outcome | P15-05 | L | ready |
| [P15-07](P15-07-run-a-terminal-quiz-through-quizservice.md) | Run an interactive terminal quiz over a Knowledge Base through QuizService | P15-03, P15-04 | M | ready |
| [P15-08](P15-08-document-the-cli-and-close-phase-15.md) | Document the CLI entry points and close Phase 15 in the plan and roadmap | P15-06, P15-07 | S | ready |

Coverage: 15.1 -> P15-01, 02, 03, 04, 05, 06, 08; 15.2 -> P15-01, 03, 04, 07, 08.
Decisions: D01 -> P15-01, 06, 08; D02 -> P15-01; D03 -> P15-03; D04 -> P15-04, 05, 07, 08; D05 -> P15-02;
D06 -> P15-04; D07 -> P15-05; D08 -> P15-03, 06; D09 -> P15-07.
P15-04 has no dependency and may run in parallel with P15-01..03. P15-07 may run in parallel with P15-05/06.
