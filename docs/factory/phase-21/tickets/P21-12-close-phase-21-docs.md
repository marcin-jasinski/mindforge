---
id: P21-12
title: Close Phase 21 in the plan, roadmap, index, deployment guide and project state
status: ready
plan_tasks: [21.1, 21.2, 21.3]
decisions: [D07]
depends_on: [P21-01, P21-02, P21-03, P21-04, P21-05, P21-06, P21-07, P21-08, P21-09, P21-10, P21-11]
size: S
issue: 77
commit:
fix_rounds: 0
---
## Goal
The project docs show Phase 21 as complete and describe what was built: plan tasks 21.1–21.3 and the completion
checklist are checked with "As built" notes, the roadmap and `docs/INDEX.md` are current, `deployment.md` has one
coherent "CI and quality gates" description, and the CLAUDE.md "Project State" paragraph is updated. The human-only
actions are listed.

## Context
- Plan: `docs/project/implementation-plan.md` § "## [ ] Phase 21" (~line 1775 onward): tasks 21.1, 21.2, 21.3 and a
  four-item Completion Checklist. Use the "As built" style of Phase 20 (~lines 1738–1760).
- Roadmap: `docs/project/roadmap.md` line ~13 ("**Next**: Phase 21 ...") and ~134 (Phase 21 entry).
- `docs/INDEX.md` § Architecture Decision Records (~line 46): extend the ADR summary with 0042 (E2E model answers come
  from a fake OpenAI-compatible server). Also list any new doc sections or config locations if INDEX lists such things
  (`config/checkstyle/`, `config/spotbugs-exclude.xml`, `src/test/e2e/`).
- `CLAUDE.md` § Project State (only that paragraph, per `.claude/factory.md` docs_to_update): it says work resumes at
  Phase 21.
- Earlier tickets already edited parts of `docs/project/deployment.md` (P21-02 Checkstyle, P21-03 SpotBugs,
  P21-07 dependency-check workflow, P21-08 `compose.e2e.yml`, P21-11 "Run the E2E locally"),
  `docs/project/tech-stack.md` (P21-02/03/04/09) and `docs/standards/testing/test-writing.md` (P21-09). Read the Notes
  of every P21 ticket for deferred items and wording to carry into the plan.
- Facts to record as built (verify each against the code before writing):
  - 21.1: CI triggers on every push and PR (the plan's "Push to `main`" maps to this repo's `develop` and default
    branch, Q04); CI runs `mvn -B verify`, not `mvn test` (Q05); lifecycle order is Checkstyle (validate) -> compile
    -> tests (surefire + failsafe with Testcontainers) -> SpotBugs (verify) -> JaCoCo 70% check (D03), which differs
    from the plan's "Checkstyle -> SpotBugs -> tests" order; reports uploaded; scheduled + manual dependency-check
    workflow (#63); separate `e2e` job (D08).
  - 21.2: path kept (`src/test/e2e/`); "stub-provider profile" became the test-only `compose.e2e.yml` override with a
    WireMock OpenAI-compatible stub (D06, ADR 0042); Playwright Test as its own npm project (D07); export "validates"
    means a successful `<slug>-okf.zip` download, since the server validates (Q15).
  - 21.3: ArchUnit test dependency; full layer table (Q10); controller rule over `domain.port..` and
    `infrastructure.persistence..` (D11); agent independence; T25 rule; T26 as a source-scan guard with
    `BundleConformanceValidator` allowed by exact path (D12).
  - Baseline: test-classpath SPA fixture makes `theLoginRouteIsTheSinglePageApp` pass under `-DskipFrontend` (D14).
- Human-only actions (cannot be done by the factory): set repository secret `NVD_API_KEY`; mark the CI checks
  (`linux`, and `e2e` if wanted) as required status checks in branch protection of `develop` and the default branch,
  so that a failing JaCoCo gate blocks a merge.

## Decisions to apply
**D07 — Implementation notes (docs part):** `docs/standards/testing/test-writing.md` § Test Discovery and Structure
keeps the three Java lines and adds `src/test/e2e/ — browser E2E (Playwright Test, TypeScript) against compose; not run
by Maven; CI e2e job`. Plan 21.2: keep the path; change "stub-provider profile" to the `compose.e2e.yml` override
(D06). `docs/project/tech-stack.md`: name the binding `@playwright/test` and why. `docs/project/deployment.md`
"Run the E2E locally" (D08) uses the same commands. (Check each is present; add what an earlier ticket missed.)

## Acceptance criteria
- [ ] AC1: Plan Phase 21 heading is `## [x] Phase 21`; tasks 21.1, 21.2, 21.3 are `[x]`, each with an "As built" note covering the facts in Context; the 21.2 text says `compose.e2e.yml` override instead of "stub-provider profile"; the 21.3 T26 line names the `BundleConformanceValidator` exception.
- [ ] AC2: Completion checklist items are checked; "JaCoCo gate blocks PRs below 70%" notes that the check fails below 70% and that making it a required check is a human action.
- [ ] AC3: The plan or roadmap lists the follow-ups: FindSecBugs "deferred by D02" (if P21-03 did not already), the human actions, and any deferred item from ticket Notes.
- [ ] AC4: `docs/project/roadmap.md`: Phase 21 marked complete with a one-line summary; the "Next" line no longer points at Phase 21 (state what remains, e.g. Phase 14's manual Langfuse check).
- [ ] AC5: `docs/INDEX.md` mentions ADR 0042 and any new doc sections; descriptions stay concise.
- [ ] AC6: `docs/project/deployment.md` has a short "CI and quality gates" overview (jobs, what each runs, `-Dcheckstyle.skip` / `-Dspotbugs.skip`, where reports go) that links the sections earlier tickets wrote, with no contradiction between them.
- [ ] AC7: `CLAUDE.md` changes only in the "Project State" paragraph: Phase 21 is complete and the remaining work is named.
- [ ] AC8: `mvn -B verify -DskipFrontend=true` is green (docs only).

## Test plan
- Docs only; no test. Check by reading each file and by `grep` for stale phrases ("No CI job runs the check yet", "Work resumes at Phase 21", "stub-provider profile"). Factory gate for AC8.

## Files likely touched
- `docs/project/implementation-plan.md`
- `docs/project/roadmap.md`
- `docs/INDEX.md`
- `docs/project/deployment.md`
- `CLAUDE.md` (Project State paragraph only)
- `docs/standards/testing/test-writing.md`, `docs/project/tech-stack.md` (only if an earlier ticket missed its part)

## Out of scope
- Any code, workflow or config change.
- Other CLAUDE.md sections.
- Writing REPORT.md (the factory does that; the human actions go there too).

## Notes
