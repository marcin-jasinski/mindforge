# Factory run report — Phase 15 (CLI Entry Points)

- **Run:** phase-15 · started 2026-11-11 · finished 2026-11-13
- **Outcome:** halted for human review. All 8 tickets are done. The branch is not merged and not committed by this step.
- **Branch:** `factory/phase-15` · base 6cacd73848ee0819341f381714b97b4dca842864

## Audit first

1. Manual check is open: run `java -jar mindforge.jar --spring.profiles.active=cli pipeline ...` and `quiz ...` against a real database and a real model. No automated test uses a real model.
2. D04 (majority, ADR-0019): the CLI acts as a named user (`--user=<email>`) with no password. The architect and skeptic voted B, the pragmatist voted A. Check the trust model in `docs/adr/0019-cli-acts-as-a-named-user-without-password.md`.
3. D06 and D07 are judge decisions with one ballot each. Check them: add `UploadSource.CLI`; fixed extension map for the MIME type.
4. Known baseline failure: `ApiFlowTest.theLoginRouteIsTheSinglePageApp` (404 with `-DskipFrontend`). It is not caused by this phase.
5. Deferred findings (below): P15-08 has four minor doc mistakes in `deployment.md` (exit-code table, queued-run wait, re-queue claim). P15-03 has a usage text that shows space-separated option values, which Spring does not parse.

## Tickets

| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P15-01 | Boot the cli profile without the web stack and without the run sweep | done | bf4fd47 | 0 | |
| P15-02 | Print run progress to the console in the cli profile | done | 278139f | 0 | |
| P15-03 | Select the CLI command and exit with a stable code | done | 491ff8c | 1 | |
| P15-04 | Resolve a user by email and add the CLI upload source | done | fc979b4 | 0 | |
| P15-05 | Admit a file through the pipeline command | done | 6c58b61 | 0 | |
| P15-06 | Run the ingest in process and report its outcome | done | 9345cfe | 0 | |
| P15-07 | Run a terminal quiz through QuizService | done | 8362ce8 | 0 | |
| P15-08 | Document the CLI and close Phase 15 | done | 1da1e91 | 1 | |

No ticket is parked.

## Decisions

| Id | Question | Method | Class | Decision (one line) | Dissent / risk to watch |
|---|---|---|---|---|---|
| D01 | Where the CLI run executes | unanimous | local | Run the ingest in the CLI process, with no sweep (`@Profile("!cli")` on `runWorkerTriggers`) | Server must be stopped first (one live instance) |
| D02 | CLI profile and web stack | unanimous | local | `cli` profile is non-web; web-only beans get `@ConditionalOnWebApplication(SERVLET)` | None |
| D03 | Runner selection | unanimous | local | First non-option argument is the command (`pipeline`, `quiz`) | None |
| D04 | CLI acting user | majority (A vs B, B won) | adr | Required `--user=<email>`; ownership check; no password | Pragmatist voted A. Trust model: whoever runs the CLI can name any user. |
| D05 | Progress to terminal | unanimous | local | `ConsoleProgressNotifier` replaces the SSE notifier in `cli` | None |
| D06 | CLI upload source | judge | local | Add `UploadSource.CLI` | Single ballot. Code that branches on `== API` would exclude CLI documents. |
| D07 | CLI MIME type | judge | local | Fixed case-insensitive extension map; unknown gives `application/octet-stream` | Single ballot |
| D08 | Pipeline outcome and flags | unanimous | local | Exit 0/1/2 table; `--lesson-id`, `--new-version` | None |
| D09 | Quiz CLI flow | unanimous | local | Sync `QuizService.start` over the whole KB; read answers from stdin | None |

Method counts: unanimous 6 · majority 1 · debate 0 · judge 2. Blockers: 0.

## Decision outcomes

| Decision | Method | Vote changes | Models (panel / judge) | Tickets applying it (status, fix rounds) | Findings / rulings citing it |
|---|---|---|---|---|---|
| D01 | unanimous | none | opus x3 / opus | P15-01 (done, 0), P15-06 (done, 0), P15-08 (done, 1) | P15-0x spec-security reviews cite the D-ids; no finding disputes it |
| D02 | unanimous | none | sonnet x3 / opus | P15-01 (done, 0) | P15-01 spec-security review |
| D03 | unanimous | none | sonnet x3 / opus | P15-03 (done, 1) | P15-03 r1/r2 correctness and spec-security: S1 minor (usage shows space-separated values) |
| D04 | majority | none | opus x3 / opus | P15-04 (done, 0), P15-05 (done, 0), P15-07 (done, 0), P15-08 (done, 1) | P15-04, 05, 07 spec-security reviews; no finding against the decision |
| D05 | unanimous | none | sonnet x3 / opus | P15-02 (done, 0) | P15-02 spec-security review |
| D06 | judge | none (one ballot) | sonnet / opus | P15-04 (done, 0) | P15-04 spec-security review |
| D07 | judge | none (one ballot) | sonnet / opus | P15-05 (done, 0) | P15-05 spec-security review |
| D08 | unanimous | none | sonnet x3 / opus | P15-03 (done, 1), P15-06 (done, 0) | P15-06: S1 minor (repeated `--lesson-id` treated as absent) |
| D09 | unanimous | none | sonnet x3 / opus | P15-07 (done, 0) | P15-07 spec-security review |

The "findings citing" column comes from `grep -l "D0N"` on `reviews/`. The reviews cite the D-ids as context. This step did not read each finding in full. No ruling files exist.

## Panel statistics

Decisions per method: unanimous 6, majority 1, judge 2 · round-2 debates 0 · vote changes 0 · ballots discounted for false evidence 0 (no `counted: no` row in any tally) · disputed findings: valid 0, invalid 0, defer 0 (no `rulings/` directory).

## ADRs written

- ADR-0019 `docs/adr/0019-cli-acts-as-a-named-user-without-password.md` (from D04). Now listed in `docs/INDEX.md`.

## Deferred findings

- P15-02: standards nit (0.5) `CliProfileContextTest.java:30` import order.
- P15-03: spec-security minor (0.5) `CliUsageRunner.java:17` usage shows `--file <path>` but Spring reads values only from `--name=value`; standards nit `CliProfileContextTest.java:30`.
- P15-06: correctness nit (0.8) `PipelineCommandRunnerTest.java:680` assertion `err.size()` cannot fail; spec-security minor (0.6) `PipelineCommandRunner.java:245` repeated `--lesson-id` is read as absent.
- P15-07: standards nit (0.6) `CliExitOnReadyWiringTest.java:28` fully qualified `QuizService` name.
- P15-08: `deployment.md:80` identical file with a QUEUED run waits for that run (C1); `deployment.md:99` exit-code table misses two paths that return 2 (C2); `deployment.md:126` re-queue claim is not always true, and a CLI run stays RUNNING after a timeout (C3); `deployment.md:100` exit code 2 text incomplete (S2).
- Holistic: H1 (INDEX.md lacks ADR-0019 and the CLI) was fixed by this step. H2 (`CliUsageRunner.java:20` and `CliExit` javadoc give an incomplete meaning of exit code 1) and H3 (`option`/`parseUuid` helpers copied in `PipelineCommandRunner` and `QuizCommandRunner`; move to `CliCommand`) are still open.

## Manual checks still open

- Run `java -jar mindforge.jar --spring.profiles.active=cli pipeline --file=<path> --kb=<id> --user=<email>` and `... quiz --kb=<id> --user=<email>` against a real database and a real model.
- Phase 14 manual check (Langfuse traces for a real run) is still open. It is outside this phase.

## Statistics

Tickets done 8, parked 0. Total fix rounds 2 (P15-03, P15-08). Decisions per method: unanimous 6, majority 1, judge 2. Disputed findings 0. Forced continuations 4, stalled 0.

Agent calls (77 total) per role and model:

| Role | Model | Calls |
|---|---|---|
| planner | opus | 1 |
| solver | opus | 6 |
| solver | sonnet | 18 |
| judge | opus | 8 |
| ticket-writer | opus | 1 |
| implementer | default | 8 |
| tester | sonnet | 9 |
| reviewer | opus | 17 |
| reviewer | sonnet | 6 |
| fixer | default | 2 |
| scribe | sonnet | 1 |
