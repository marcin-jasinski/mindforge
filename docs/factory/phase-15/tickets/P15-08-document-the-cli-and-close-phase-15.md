---
id: P15-08
title: Document the CLI entry points and close Phase 15 in the plan and roadmap
status: done
plan_tasks: [15.1, 15.2]
decisions: [D01, D04]
depends_on: [P15-06, P15-07]
size: S
issue: 9
commit: 1da1e91
fix_rounds: 1
---
## Goal
The docs describe how to run `mindforge-pipeline` and `mindforge-quiz`, their flags and exit codes, the trust model of
`--user`, and why the server must be stopped first. Plan tasks 15.1/15.2 and the completion checklist are ticked with
"as built" notes; the roadmap and CLAUDE.md "Project State" show Phase 15 done.

## Context
- `docs/project/implementation-plan.md` `## [ ] Phase 15 — CLI Entry Points` (~line 1438): tasks 15.1, 15.2 and the
  checklist ("Both CLI runners work via `java -jar mindforge.jar --spring.profiles.active=cli`", "CLI quiz submits
  answers through the same `QuizService` path as the web UI").
- `docs/project/roadmap.md`: "Next" line (~line 13) and the Phase 15 entry (~line 105).
- `docs/project/deployment.md`: sections Build, Run locally, Configuration, Railway, One live instance (~line 57).
- `CLAUDE.md`: only the "Project State" paragraph may change ("Work resumes at Phase 14 ..."). Check the roadmap for
  the real next phase (Phase 14 has an open manual checklist item; Phases 14, 15, 16 are parallel after 13).
- `docs/INDEX.md`: change only if a new doc file is added (this ticket should not add one).
- As built (from P15-01..P15-07): commands `pipeline --file=<path> --kb=<id> --user=<email> [--lesson-id=<id>]
  [--new-version]` and `quiz --kb=<id> --user=<email>`; exit codes 0 success, 1 run failed or timed out, 2 input
  refused; `mindforge.cli.run-timeout` (default PT30M); `UploadSource.CLI`; under `cli` no web server and no run sweep.
  Read the merged code to confirm before you write.
- Docs are written in ASD-STE100 Simplified Technical English where the house rules require it; follow the style of
  the surrounding text.

## Decisions to apply
**D01 — (docs part):** `docs/project/deployment.md#one-live-instance`: add a CLI paragraph — the CLI is a live instance
and runs no sweep; stop the server before `--spring.profiles.active=cli` on the same database (or use a different
database), otherwise the server's sweep fails the CLI's run within one sweep interval and re-queues it. A further QUEUED
run that `drain` claims just before the CLI exits is left RUNNING and the next server start re-queues it.

**D04 — (docs part):** `deployment.md` — the CLI trusts the operator (database credentials are the boundary; treat
them as administrator access), takes `--user`, asks for no password, and the per-user API rate limit does not apply.
Record `--user` as an "as built" note on plan tasks 15.1/15.2. Reference ADR-0019.

Also from D03: document both command lines in `deployment.md` (CLI section) under the labels `mindforge-pipeline` /
`mindforge-quiz`. From D08: document the exit codes as a stable script contract.

## Acceptance criteria
- [x] AC1: `deployment.md` has a CLI section with both command lines (labels `mindforge-pipeline`, `mindforge-quiz`),
      all flags, the three exit codes and `mindforge.cli.run-timeout`.
- [x] AC2: `deployment.md#one-live-instance` has the D01 paragraph (stop the server first; consequence if not).
- [x] AC3: `deployment.md` states the D04 trust model and links ADR-0019.
- [x] AC4: In `implementation-plan.md`, tasks 15.1 and 15.2 and both checklist items are ticked, with an "as built"
      note for `--user`, `ApplicationRunner` (instead of `CommandLineRunner`) and the `pipeline`/`quiz` subcommands; the
      Phase 15 heading is ticked.
- [x] AC5: `roadmap.md` marks Phase 15 done and its "Next" line is correct.
- [x] AC6: Only the "Project State" paragraph of `CLAUDE.md` changes.
- [x] AC7: `docs/INDEX.md` is unchanged unless a new doc was added.

## Test plan
Documentation only: review against the merged code (`dev.mindforge.cli`, `application-cli.yml`, `AppProperties`).
`mvn -B verify -DskipFrontend=true` must still pass (no code change).

## Files likely touched
- `docs/project/deployment.md`
- `docs/project/implementation-plan.md`
- `docs/project/roadmap.md`
- `CLAUDE.md` ("Project State" paragraph only)

## Out of scope
- Code changes; wrapper scripts; new doc files; editing ADR-0019 or other ADRs.
- Phase 14's open manual checklist item.

## Notes
Decided: roadmap "Next" and CLAUDE.md keep Phase 14 first (only its manual Langfuse check is open), then Phase 16, because Phase 14 is not ticked and Phase 16 is the first unstarted phase.
Decided: deployment.md exit code 1 also names the wait timeout and unexpected errors, because PipelineCommandRunner and QuizCommandRunner return RUN_FAILED for both.

### Deferred findings
- correctness/C1 minor 0.85 docs/project/deployment.md:80: The deployment guide says an identical file means "no run is queued ... and does not wait", but when the latest run of the identical document is still QUEUED, the command drains the queue and waits for that run.
- correctness/C2 minor 0.8 docs/project/deployment.md:99: The exit-code table leaves out two code paths that return 2. The pipeline returns 2 when it cannot read the file, and the quiz returns 2 when the knowledge base has nothing to quiz. The table also says "before a run was queued" for the quiz, which never queues a run.
- correctness/C3 minor 0.7 docs/project/deployment.md:126: The guide says "the next server start re-queues it" for a run that the CLI claimed and left RUNNING. This is not always true. The sweep re-queues only INGEST runs with attempt < 3, and the CLI drain can also claim a QUEUED LINT run. The guide also does not tell the operator that the CLI's own run stays RUNNING after a timeout (exit 1). The server later fails that run and runs it again.
- spec-security/S2 minor 0.7 docs/project/deployment.md:100: The description of exit code 2 leaves out two refusals that the code reports as 2, and its claim that a retry with the same input always fails again is not true.
