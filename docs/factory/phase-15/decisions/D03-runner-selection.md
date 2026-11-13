---
id: D03
question: Q03
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q03.r1.tally.md]
adr:
---
## Decision
Use A: the first non-option argument is the command. Run `java -jar mindforge.jar --spring.profiles.active=cli pipeline --file <path> --kb <id>` or `... --spring.profiles.active=cli quiz --kb <id>`. Each runner reads the command from `ApplicationArguments.getNonOptionArgs()` and returns at once if the command is not its own. A missing or unknown command prints usage to stderr and ends the process with a non-zero exit code. `mindforge-pipeline` and `mindforge-quiz` are documentation labels only; the concrete subcommands are `pipeline` and `quiz`. Do not add wrapper scripts or sub-profiles.
## Why
- Checklist requires one entry point for both runners: `--spring.profiles.active=cli` (implementation-plan.md:1455). A keeps it as written; C needs `cli,pipeline` / `cli,quiz`.
- No scripts exist and deployment.md/Procfile describe only the web start, so D adds speculative files (minimal-implementation).
- B selects by inference; a missing `--file` silently starts the quiz (verified hard objection).
- A is one guard line per runner, and the command check is a pure function of the args, unit-testable without a Spring context.
- All three solvers chose A at 0.8.
## Rejected
- B: implicit selection by `--file` presence; silent wrong-command failure.
- C: changes the checklist entry point; with `cli` alone nothing runs.
- D: wrapper scripts are not requested and not testable here; can be added later at no cost.
## Dissent
None. Risk to watch (skeptic): a CLI process next to a live server interferes with run recovery (scope.md:41); that is Q01's decision, not this one.
## Implementation notes
- Package `dev.mindforge.cli`: `PipelineCommandRunner` and `QuizCommandRunner` implement `ApplicationRunner` (Spring's argument-parsed sibling of `CommandLineRunner`; the plan's intent is kept). Wire them as beans in a `@Configuration` class annotated `@Profile("cli")`; constructor injection.
- One small shared helper, e.g. `final class CliCommand` with `static Optional<String> of(ApplicationArguments args)` returning the first non-option arg, and constants `PIPELINE = "pipeline"`, `QUIZ = "quiz"`. No command framework.
- Missing/unknown command handling lives in one place (a third `@Profile("cli")` runner or the helper): print usage listing `pipeline --file <path> --kb <id>` and `quiz --kb <id>`, and produce a non-zero exit code (throw a dedicated CLI exception that maps through `ExitCodeExceptionMapper`, or use `ExitCodeGenerator` + `SpringApplication.exit`). Runner failures also exit non-zero.
- Missing required options (`--file`, `--kb`) for the chosen command: usage + non-zero exit.
- `application-cli.yml`: `spring.main.web-application-type: none` (worker/recovery behaviour is Q01's call).
- Tests (plain Mockito, `DefaultApplicationArguments`): `pipeline` runs only the pipeline runner; `quiz` runs only the quiz runner; `--spring.profiles.active=cli` alone gives usage + non-zero; unknown `foo` gives usage + non-zero; `pipeline` without `--file` gives usage + non-zero.
- Document both command lines in deployment.md (CLI section) under the labels `mindforge-pipeline` / `mindforge-quiz`.
