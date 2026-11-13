---
id: Q03
status: decided
decision: D03
impact: medium
blocks: [W01, W03, W05]
---
## Question
With one `cli` profile and two runners (`mindforge-pipeline`, `mindforge-quiz`), how does an invocation choose which
runner runs, and what are `mindforge-pipeline` / `mindforge-quiz` concretely (subcommand names, scripts, or labels)?

## Context
- Plan 15.1/15.2 name two CLIs; the checklist says both work via `java -jar mindforge.jar --spring.profiles.active=cli`.
- Under one profile both `CommandLineRunner` beans run unless each checks its own trigger.
- `deployment.md` and `Procfile` describe only the web start; no wrapper scripts exist.

## Known options
- A: First non-option argument is the command: `... --spring.profiles.active=cli pipeline --file x --kb y` /
  `... quiz --kb y`. Each runner returns at once if the command is not its own; unknown command → usage, non-zero exit.
- B: Selection by options: `--file` present → pipeline; otherwise quiz.
- C: Two sub-profiles (`cli,pipeline` / `cli,quiz`) with `@Profile` on each runner.
- D: A or C plus two shell scripts named `mindforge-pipeline` / `mindforge-quiz` that wrap the `java -jar` line.

## Constraints
- Checklist entry point `java -jar mindforge.jar --spring.profiles.active=cli` must work for both.
- Thin adapters (hexagonal.md); minimal implementation (no speculative command framework).
