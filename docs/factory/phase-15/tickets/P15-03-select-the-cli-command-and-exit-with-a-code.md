---
id: P15-03
title: Select the CLI command from the first argument and exit with a stable code
status: ready
plan_tasks: [15.1, 15.2]
decisions: [D03, D08]
depends_on: [P15-02]
size: M
issue: 4
commit:
fix_rounds: 0
---
## Goal
In the `cli` profile, the first non-option argument names the command (`pipeline` or `quiz`). A missing or unknown
command prints usage to stderr and the process ends with exit code 2. After the runners finish, the CLI process closes
its context through `SpringApplication.exit` and ends with the exit code the runners report (0, 1 or 2). The
`pipeline` and `quiz` runners themselves are P15-05/06 and P15-07.

## Context
- After P15-02: `dev.mindforge.cli.CliConfig` (`@Configuration @Profile("cli")`) and `ConsoleProgressNotifier` exist;
  `integration/cli/CliProfileContextTest` boots the `cli` context with no program arguments.
- In a non-web context with `@EnableScheduling` the JVM does not exit by itself after the runners return, so the cli
  context must be closed and the process ended explicitly. `MindForgeApplication.main` must not change (D02).
- `ApplicationRunner` / `ApplicationArguments` (`DefaultApplicationArguments` in unit tests), `ExitCodeGenerator` and
  `SpringApplication.exit(ApplicationContext, ExitCodeGenerator...)` are Spring Boot APIs; `SpringApplication.exit`
  also uses every `ExitCodeGenerator` bean and returns the first non-zero code.
- Rules: `cli` is a thin adapter, no business logic; beans in `@Configuration`, constructor injection; logger `log`;
  `record`/enum for value types; unit tests without a Spring context (`docs/standards/testing/test-writing.md`).

## Decisions to apply
**D03 — Decision:** The first non-option argument is the command. Run `java -jar mindforge.jar
--spring.profiles.active=cli pipeline --file <path> --kb <id>` or `... --spring.profiles.active=cli quiz --kb <id>`.
Each runner reads the command from `ApplicationArguments.getNonOptionArgs()` and returns at once if the command is not
its own. A missing or unknown command prints usage to stderr and ends the process with a non-zero exit code.
`mindforge-pipeline` and `mindforge-quiz` are documentation labels only; the concrete subcommands are `pipeline` and
`quiz`. Do not add wrapper scripts or sub-profiles.

**D03 — Implementation notes:**
- Package `dev.mindforge.cli`: `PipelineCommandRunner` and `QuizCommandRunner` implement `ApplicationRunner`; wire them
  as beans in the `@Profile("cli")` configuration; constructor injection. (They are added in later tickets.)
- One small shared helper, e.g. `final class CliCommand` with `static Optional<String> of(ApplicationArguments args)`
  returning the first non-option arg, and constants `PIPELINE = "pipeline"`, `QUIZ = "quiz"`. No command framework.
- Missing/unknown command handling lives in one place (a third `@Profile("cli")` runner or the helper): print usage
  listing both commands, and produce a non-zero exit code (`ExitCodeGenerator` + `SpringApplication.exit`, or a
  dedicated CLI exception mapped through `ExitCodeExceptionMapper`). Runner failures also exit non-zero.
- Missing required options for the chosen command: usage + non-zero exit (applied by each runner's ticket).
- Tests (plain Mockito, `DefaultApplicationArguments`): `--spring.profiles.active=cli` alone gives usage + non-zero;
  unknown `foo` gives usage + non-zero. (`pipeline`/`quiz` routing tests are in the runner tickets.)

**D08 — Decision (part applied here):** The CLI ends with one of three stable exit codes: **0** = success, **1** = the
ingest ran (or ran before) and failed, **2** = the input was refused before any run was queued (usage error, unknown
user/KB/not owner, or a refused upload). Exit codes are a script contract: document them in the usage text and never
renumber them.

**D08 — Implementation notes (part applied here):**
- One small mapping in `dev.mindforge.cli`, e.g. `enum CliExit { OK(0), RUN_FAILED(1), REFUSED(2) }`; runners implement
  `ExitCodeGenerator`; exit goes through `SpringApplication.exit` so the context closes (and `RunWorker.stop()` can
  run). Never `System.exit` inside a runner.
- Usage text lists the flags and the three exit codes. Usage error -> 2.

## Ticket-writer decision (how the process ends)
D02 forbids changing `main`, and D08 forbids `System.exit` in a runner, while a test context must not end the test JVM.
Therefore: add one `@Profile("cli")` listener bean (e.g. `CliExitOnReady`, in `CliConfig`) for `ApplicationReadyEvent`
(it fires after all runners) that calls `System.exit(SpringApplication.exit(context))`. Register it only when property
`mindforge.cli.exit-when-done` is `true` (`@ConditionalOnProperty(..., matchIfMissing = true)`); set it to `false` in
the cli integration tests. Runners report outcomes only through `ExitCodeGenerator` and never throw for an expected
outcome, so a test context with no arguments still loads (it prints usage and reports 2, and nothing exits).

## Acceptance criteria
- [ ] AC1: `CliCommand.of(args)` returns the first non-option argument, or empty when there is none.
- [ ] AC2: With no non-option argument, the usage runner prints usage to stderr and its `getExitCode()` is 2.
- [ ] AC3: With an unknown first argument (e.g. `foo`), usage is printed to stderr and the exit code is 2.
- [ ] AC4: With `pipeline` or `quiz` as first argument, the usage runner prints nothing and its exit code is 0.
- [ ] AC5: The usage text contains `pipeline --file <path> --kb <id> --user <email> [--lesson-id <id>]
      [--new-version]`, `quiz --kb <id> --user <email>`, and the meaning of exit codes 0, 1 and 2.
- [ ] AC6: In the `cli` context with `mindforge.cli.exit-when-done=false` the context loads with no arguments and no
      exit listener bean exists; with the default (true) the listener bean exists in the `cli` profile only.
- [ ] AC7: No `System.exit` call exists in any runner class.

## Test plan
- AC1–AC5: unit tests `src/test/java/dev/mindforge/unit/cli/CliCommandTest.java` and
  `src/test/java/dev/mindforge/unit/cli/CliUsageRunnerTest.java` (new); `DefaultApplicationArguments`, a
  `ByteArrayOutputStream` stderr passed through the constructor; no Spring context; AssertJ.
- AC6: `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java` sets
  `mindforge.cli.exit-when-done=false` and asserts the listener bean is absent; a context-runner or bean-definition
  check (or a second small test) asserts it is present when the property is not set — without starting the exit.
- AC7: review.

## Files likely touched
- `src/main/java/dev/mindforge/cli/CliCommand.java` (new)
- `src/main/java/dev/mindforge/cli/CliExit.java` (new)
- `src/main/java/dev/mindforge/cli/CliUsageRunner.java` (new)
- `src/main/java/dev/mindforge/cli/CliExitOnReady.java` (new)
- `src/main/java/dev/mindforge/cli/CliConfig.java`
- `src/main/resources/application-cli.yml` (`mindforge.cli.exit-when-done: true`)
- `src/test/java/dev/mindforge/unit/cli/CliCommandTest.java` (new)
- `src/test/java/dev/mindforge/unit/cli/CliUsageRunnerTest.java` (new)
- `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java`

## Out of scope
- The `pipeline` and `quiz` runners (P15-05, P15-06, P15-07).
- Wrapper scripts, sub-profiles, a command framework (picocli etc.).
- Changing `MindForgeApplication.main`.

## Notes
