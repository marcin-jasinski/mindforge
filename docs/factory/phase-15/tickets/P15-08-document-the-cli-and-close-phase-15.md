---
id: P15-08
title: Document the CLI entry points and close Phase 15 in the plan and roadmap
status: ready
plan_tasks: [15.1, 15.2]
decisions: [D01, D04]
depends_on: [P15-06, P15-07]
size: S
issue:
commit:
fix_rounds: 0
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
- As built (from P15-01..P15-07): commands `pipeline --file <path> --kb <id> --user <email> [--lesson-id <id>]
  [--new-version]` and `quiz --kb <id> --user <email>`; exit codes 0 success, 1 run failed or timed out, 2 input
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
- [ ] AC1: `deployment.md` has a CLI section with both command lines (labels `mindforge-pipeline`, `mindforge-quiz`),
      all flags, the three exit codes and `mindforge.cli.run-timeout`.
- [ ] AC2: `deployment.md#one-live-instance` has the D01 paragraph (stop the server first; consequence if not).
- [ ] AC3: `deployment.md` states the D04 trust model and links ADR-0019.
- [ ] AC4: In `implementation-plan.md`, tasks 15.1 and 15.2 and both checklist items are ticked, with an "as built"
      note for `--user`, `ApplicationRunner` (instead of `CommandLineRunner`) and the `pipeline`/`quiz` subcommands; the
      Phase 15 heading is ticked.
- [ ] AC5: `roadmap.md` marks Phase 15 done and its "Next" line is correct.
- [ ] AC6: Only the "Project State" paragraph of `CLAUDE.md` changes.
- [ ] AC7: `docs/INDEX.md` is unchanged unless a new doc was added.

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
