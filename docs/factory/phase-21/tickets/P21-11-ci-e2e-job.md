---
id: P21-11
title: Run the browser E2E journey in a CI job against the compose stack
status: ready
plan_tasks: [21.1, 21.2]
decisions: [D08, D07]
depends_on: [P21-07, P21-10]
size: S
issue: 76
commit:
fix_rounds: 0
---
## Goal
A CI job `e2e` in `.github/workflows/ci.yml` runs on every push and PR after the `linux` job passes. It builds and
starts the stack with `compose.yml` + `compose.e2e.yml`, runs the Playwright suite in `src/test/e2e/`, uploads the
report, traces and WireMock unmatched requests, and tears the stack down. `docs/project/deployment.md` explains how to
run the same suite locally.

## Context
- `.github/workflows/ci.yml` after P21-07: `on: push:` + `pull_request:`; jobs `linux` (`mvn -B verify`, reports) and
  `windows` (`mvn -B package`).
- P21-08: `compose.e2e.yml` (WireMock `model-stub`, no published port). P21-09/P21-10: Playwright project in
  `src/test/e2e/` (`package-lock.json`, `npm test`, reports to `playwright-report/` and `test-results/`).
- The app image builds the SPA and JAR from the `Dockerfile` (`compose.yml` `app.build: .`).
- `docs/project/deployment.md:15-16` describes the Java failsafe "e2e/ tests"; the browser suite is a different thing.

## Decisions to apply
**D08 — Decision:** add a separate `e2e` job to `.github/workflows/ci.yml` that runs on every push and PR (the existing
`push:`/`pull_request:` triggers), `needs: linux`, `runs-on: ubuntu-latest`. The job builds the image with compose,
starts the stack, and runs the browser suite outside Maven. Do not bind the suite to `mvn verify` in any profile.

**D08 — Implementation notes:**
- Job `e2e`: `needs: linux`, `runs-on: ubuntu-latest`; compose build, compose up `-d --wait`, run the suite, upload the
  report and traces with `if: always()`, compose `down -v` with `if: always()`. Pin the browser version (it is pinned
  by the exact `@playwright/test` version in the lockfile). Always pass `-f compose.yml -f compose.e2e.yml` (D06: a
  bare `docker compose` would merge `compose.override.yml` and miss the stub).
- Place the suite outside `src/test/java` (already done in P21-09).
- `docs/project/deployment.md`: add "Run the E2E locally" with the exact compose and suite commands. State that
  `mvn verify` and the factory gate do not run it. Keep it separate from the existing "e2e/ tests (failsafe)" text so
  the two cannot be confused.
- Risk to watch: a flaky E2E turns every push red; if it becomes a real cost, narrow the trigger (one-line change).

**D07 — Implementation notes (CI part):** `actions/setup-node@v4` node 22, `cache: npm`,
`cache-dependency-path: src/test/e2e/package-lock.json`; in `working-directory: src/test/e2e`: `npm ci`,
`npx playwright install --with-deps chromium`, `npm test`. Upload `playwright-report/` and `test-results/` and dump
WireMock unmatched requests with `if: always()` (for example
`docker compose -f compose.yml -f compose.e2e.yml exec -T model-stub wget -qO- http://localhost:8080/__admin/requests/unmatched > unmatched.json`
plus `docker compose ... logs` into a file, uploaded with the report).

## Acceptance criteria
- [ ] AC1: `ci.yml` has a job `e2e` with `needs: linux`, `runs-on: ubuntu-latest`, no new trigger and no branch filter.
- [ ] AC2: Steps in order: checkout; setup-node 22 with the `src/test/e2e/package-lock.json` cache; `docker compose -f compose.yml -f compose.e2e.yml build`; `... up -d --wait`; `npm ci`, `npx playwright install --with-deps chromium`, `npm test` in `src/test/e2e`; then with `if: always()`: dump WireMock unmatched requests and compose logs, upload `playwright-report/`, `test-results/` and the dumps as one artifact, `... down -v`.
- [ ] AC3: Every compose command names both `-f` files; none uses `compose.override.yml`.
- [ ] AC4: The workflow file is valid YAML (parse check or `actionlint`).
- [ ] AC5: `docs/project/deployment.md` has a "Run the E2E locally" section with the exact up / `npm ci` / `npx playwright install chromium` / `npm test` / down commands, states that `mvn verify` and the factory gate do not run it, and is separate from the failsafe `e2e/` text.
- [ ] AC6: Locally, the commands of AC5 run the suite green (record in Notes). `mvn -B verify -DskipFrontend=true` is green.

## Test plan
- No new test code. AC4 by a YAML parse; AC6 by running the documented commands locally and the factory gate. The CI run on the pushed branch is the final proof; record its outcome in Notes if visible.

## Files likely touched
- `.github/workflows/ci.yml`
- `docs/project/deployment.md`

## Out of scope
- Binding the suite to Maven or a Maven profile.
- Running E2E on the Windows leg; matrix browsers; deploy jobs.
- Changing the triggers.

## Notes
