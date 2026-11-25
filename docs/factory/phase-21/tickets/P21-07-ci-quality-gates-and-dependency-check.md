---
id: P21-07
title: Run the quality gates in CI, publish their reports, and add the scheduled dependency-check workflow
status: ready
plan_tasks: [21.1]
decisions: [D03]
depends_on: [P21-03]
size: S
issue: 72
commit:
fix_rounds: 0
---
## Goal
The `linux` CI job runs Checkstyle -> tests with Testcontainers -> SpotBugs -> JaCoCo 70% check through one
`mvn -B verify` step and publishes the Checkstyle, SpotBugs and JaCoCo reports. A separate workflow runs the OWASP
dependency check weekly and on manual dispatch (#63), and fails when the check fails.

## Context
- `.github/workflows/ci.yml` exists: `on: push:` (all branches) + `pull_request:`. Job `linux`: checkout, Java 21
  temurin (maven cache), Node 22 (npm cache on `frontend/package-lock.json`), `npm ci && npm test -- --no-watch` in
  `frontend/`, `mvn -B verify`, uploads `target/site/jacoco/` with `if: always()`. Job `windows`: `mvn -B package`.
- After P21-02/P21-03, `mvn -B verify` already runs Checkstyle (`validate`), SpotBugs (`verify`, before
  `jacoco-check`) and the JaCoCo check. `mvn -B package` on Windows also runs Checkstyle (line-ending neutral rules).
- Default report paths: Checkstyle `target/checkstyle-result.xml`; SpotBugs `target/spotbugsXml.xml` (and
  `target/spotbugs.xml` for some versions — confirm locally); JaCoCo `target/site/jacoco/`.
- Dependency check: `pom.xml` ~401–409 configures `org.owasp:dependency-check-maven` (`${dependency-check-maven.version}`
  12.2.2), suppression file `dependency-check-suppressions.xml`, NVD key only from `NVD_API_KEY`; not bound to any
  phase. `docs/project/deployment.md` § Dependency check describes the manual run and says "No CI job runs the check
  yet; a scheduled job is Phase 21 work (#63)". Its report is written under `target/` (`dependency-check-report.html`).
- Do not commit any secret (`docs/standards/global/conventions.md` § Environment Variables).
- The job named `linux` must keep its name: the E2E job (P21-11) uses `needs: linux`.

## Decisions to apply
**D03 — Decision:** bind `maven-checkstyle-plugin` goal `check` to the `validate` phase and `spotbugs-maven-plugin`
goal `check` to the `verify` phase in `pom.xml`. CI keeps one `mvn -B verify` step; the lifecycle gives the order
Checkstyle -> compile -> tests -> SpotBugs -> JaCoCo check. Keep the `-Dcheckstyle.skip` / `-Dspotbugs.skip` escapes
and document them.

**Implementation notes (CI part):** CI needs no extra Checkstyle or SpotBugs step (option C, duplicating the checks
as separate CI steps, was rejected). D01's note "CI: run `mvn -B checkstyle:check` first" is satisfied by the
`validate` binding: Checkstyle is the first thing `mvn -B verify` runs. Do not add separate steps.

**Answered questions that bind this ticket:**
- **Q04** (answered by `.claude/factory.md#git` and `ci.yml#on`): keep `push` on every branch plus `pull_request`;
  the plan's "Push to `main`" maps to this repo's branches. Record that in the plan "As built" note (P21-12).
- **Q05** (answered by `deployment.md#Build`, R02): CI runs `mvn -B verify`, not `mvn test`; only `verify` runs the
  Testcontainers tests and the coverage gate.
- **Q13** (answered by `deployment.md#Dependency check`, plan Phase 20, issue #63): a separate workflow on `schedule`
  (weekly) and `workflow_dispatch` running `mvn -B dependency-check:check` with
  `NVD_API_KEY: ${{ secrets.NVD_API_KEY }}` and an `actions/cache` of
  `~/.m2/repository/org/owasp/dependency-check-data`. Not in `verify`, not on every push; key from a secret, never
  committed; cache the NVD data; a failure fails the job. Setting the secret is a human action. Update
  `deployment.md` § Dependency check to drop "No CI job runs the check yet".

## Acceptance criteria
- [ ] AC1: `ci.yml` keeps `on: push:` and `pull_request:` unchanged, keeps the single `mvn -B verify` step in `linux` (comment updated to name Checkstyle, Testcontainers tests, SpotBugs and the JaCoCo 70% gate), and keeps the `windows` job.
- [ ] AC2: The `linux` job uploads, with `if: always()`, the JaCoCo report (existing) plus the Checkstyle and SpotBugs result files (artifact names `checkstyle`, `spotbugs`, or one `quality-reports` artifact), with `if-no-files-found: ignore` where a file may be absent.
- [ ] AC3: New `.github/workflows/dependency-check.yml`: `on: schedule` (weekly cron) and `workflow_dispatch` only; Java 21 temurin with maven cache; `actions/cache@v4` on `~/.m2/repository/org/owasp/dependency-check-data` with a key that lets new NVD data be saved (for example `nvd-${{ github.run_id }}` with `restore-keys: nvd-`); step `mvn -B dependency-check:check` with `env: NVD_API_KEY: ${{ secrets.NVD_API_KEY }}`; the report uploaded with `if: always()`. No `continue-on-error`.
- [ ] AC4: Both workflow files are valid YAML (`python3 -c 'import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]' .github/workflows/*.yml`, or `actionlint` if available).
- [ ] AC5: `docs/project/deployment.md` § Dependency check names the workflow (schedule + manual, `NVD_API_KEY` secret, NVD cache) and no longer says "No CI job runs the check yet".
- [ ] AC6: `mvn -B verify -DskipFrontend=true` is green (no Maven change expected).

## Test plan
- No Java test: AC4 by a YAML parse; AC6 by the factory gate. The CI run on the pushed branch is the real proof of AC1/AC2; record its outcome in Notes if visible.

## Files likely touched
- `.github/workflows/ci.yml`
- `.github/workflows/dependency-check.yml` (new)
- `docs/project/deployment.md`

## Out of scope
- The E2E job (P21-11).
- Deploy jobs, ESLint, branch filters on `push`.
- Setting the `NVD_API_KEY` secret or branch protection (human actions, listed by P21-12).
- Binding dependency-check to any Maven phase.

## Notes
