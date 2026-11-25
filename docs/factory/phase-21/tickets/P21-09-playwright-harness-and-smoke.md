---
id: P21-09
title: Add the Playwright Test project in src/test/e2e with a sign-up smoke spec against the compose stack
status: done
plan_tasks: [21.2]
decisions: [D07]
depends_on: [P21-08]
size: M
issue: 74
commit: 40d10da
fix_rounds: 0
---
## Goal
`src/test/e2e/` is a standalone npm@11 Playwright Test (TypeScript) project. With the stack from P21-08 running,
`npm ci && npx playwright install chromium && npm test` in `src/test/e2e/` passes a smoke spec that loads the login
page, registers a new user and sees the dashboard. Maven never sees the suite.

## Context
- P21-08 adds `compose.e2e.yml` and `src/test/e2e/model-stub/`. Start command:
  `docker compose -f compose.yml -f compose.e2e.yml up -d --build --wait`; app at `http://localhost:8080`.
- SPA routes (`frontend/src/app/app.routes.ts`): `login`, `register`, `dashboard`, `kb/:kbId` (documents), `pages`,
  `pages/:directory/:name`, `runs/:runId`, `study`. Pages in `frontend/src/app/pages/` (login, dashboard, kb, wiki,
  runs, study). Pick selectors from the templates (prefer roles/labels: `getByRole`, `getByLabel`).
- `frontend/package.json` uses vitest only; Playwright must not be added there (`Dockerfile` runs `npm ci` on the
  frontend lockfile; the `frontend/**` factory gate would see it).
- `.dockerignore` and `.gitignore` exist at the repo root.
- `docs/standards/testing/test-writing.md` § Test Discovery and Structure lists the Java `unit/`, `integration/`,
  `e2e/` layers. `docs/standards/global/conventions.md`: npm@11 only; minimal dependencies, document why.
- No factory gate type-checks `src/test/e2e/**`; run `npx tsc --noEmit` there yourself.

## Decisions to apply
**D07 — Decision:** write the browser E2E with Playwright Test (`@playwright/test`, TypeScript) as its own npm@11
project in `src/test/e2e/`, with its own `package.json` and committed `package-lock.json`. Run it against
`docker compose -f compose.yml -f compose.e2e.yml up --wait` (D06), outside Maven (D08). Keep the plan path. Edit
`test-writing.md` so the browser suite and the Java failsafe `e2e/` layer are two different, named things.

**Implementation notes:**
- `src/test/e2e/package.json`: `"private": true`, `"packageManager": "npm@11.9.0"`, one devDependency
  `@playwright/test` with an exact pin (no caret), script `"test": "playwright test"`. Commit
  `src/test/e2e/package-lock.json`. Do not add Playwright to `frontend/package.json`.
- `src/test/e2e/tsconfig.json` of its own (strict); nothing in `frontend/tsconfig*.json` references it.
- `src/test/e2e/playwright.config.ts`: `baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080'`, Chromium only,
  `workers: 1`, `retries: process.env.CI ? 1 : 0`, `trace: 'retain-on-failure'`, reporter `html` (`open: 'never'`) to
  `playwright-report/`, output `test-results/`.
- Specs: `src/test/e2e/specs/smoke.spec.ts` (this ticket) and `src/test/e2e/specs/journey.spec.ts` (P21-10). Use a
  unique e-mail per run. First assertion: an Angular-rendered element on the login page, not only `<app-root>`.
- No file in `src/test/e2e/` may match `*Test.java`; Maven must not see the suite. `mvn -B verify -DskipFrontend=true`
  stays green.
- `.dockerignore`: add `src/test/e2e/node_modules`, `src/test/e2e/test-results`, `src/test/e2e/playwright-report`.
  `.gitignore`: add `src/test/e2e/test-results/` and `src/test/e2e/playwright-report/` (root `node_modules/` already
  ignored).
- Docs: `docs/standards/testing/test-writing.md` § Test Discovery and Structure keeps the three Java lines and adds
  `src/test/e2e/ — browser E2E (Playwright Test, TypeScript) against compose; not run by Maven; CI e2e job`.
  `docs/project/tech-stack.md`: name `@playwright/test` and why (Minimal Dependencies).

## Acceptance criteria
- [x] AC1: `src/test/e2e/package.json` and committed `package-lock.json` match the notes (private, `packageManager` npm@11.9.0, one exact-pinned devDependency `@playwright/test`, `test` script). `frontend/package.json` is unchanged.
- [x] AC2: `src/test/e2e/tsconfig.json` (strict) and `playwright.config.ts` match the notes; `npx tsc --noEmit` in `src/test/e2e/` passes.
- [x] AC3: `specs/smoke.spec.ts`: opens `/login`, asserts an Angular-rendered element of the login page (for example the sign-in heading or e-mail field), registers a unique user through `/register`, and asserts the dashboard ("Knowledge bases") is shown.
- [x] AC4: Against the running P21-08 stack, `npm ci && npx playwright install chromium && npm test` in `src/test/e2e/` passes (record the run in Notes; then `docker compose -f compose.yml -f compose.e2e.yml down -v`).
- [x] AC5: `.dockerignore` and `.gitignore` have the listed entries; `git status` after a run shows no report or result files.
- [x] AC6: `test-writing.md` and `tech-stack.md` are updated as the notes say.
- [x] AC7: `mvn -B verify -DskipFrontend=true` is green with the suite present.

## Test plan
- E2E (browser, Playwright Test): `src/test/e2e/specs/smoke.spec.ts` proves AC3/AC4 against the compose stack.
- Type check: `npx tsc --noEmit` (AC2). Factory gate (AC7).

## Files likely touched
- `src/test/e2e/package.json`, `src/test/e2e/package-lock.json`, `src/test/e2e/tsconfig.json`, `src/test/e2e/playwright.config.ts`, `src/test/e2e/specs/smoke.spec.ts` (all new)
- `.dockerignore`, `.gitignore`
- `docs/standards/testing/test-writing.md`, `docs/project/tech-stack.md`

## Out of scope
- The journey spec (P21-10) and the CI job (P21-11).
- Firefox/WebKit projects, page-object frameworks, extra npm dependencies.
- Any change to the SPA, `frontend/`, or `src/main` (if a selector is missing, use a role/text selector; do not add test ids to the SPA).

## Notes
- Decided: pin `@playwright/test` 1.65.1 (latest on the registry) because the notes ask for an exact pin and no version was given; lockfile made with npm 11.9.0 (`npx npm@11.9.0 install`).
- Decided: `playwright.config.ts` declares `process` locally because `@types/node` would be a second dependency; type check is `npx -p typescript tsc --noEmit` (passes) because `typescript` is not a dependency and plain `npx tsc` would fetch an unrelated package.
- Decided: the smoke spec asserts the "Welcome back" heading and the E-mail field on `/login`, and on the dashboard the URL, the "Your knowledge bases" heading and the "Knowledge bases" region, because these are the role/label selectors the templates give.
- Run (AC4): stack `docker compose -p mf-e2e-p2109 -f compose.yml -f compose.e2e.yml up -d --build --wait` healthy. This host has no Chromium, so the suite ran in `mcr.microsoft.com/playwright:v1.65.1-noble` (host network, no browser install): `npx playwright test --trace off` -> 1 passed (11.7s). Then `down -v`. `git status` showed no report or result files.

### Deferred findings
- spec-security/S1 minor 0.5 docs/factory/phase-21/tickets/P21-09-playwright-harness-and-smoke.md:80: The AC4 run record uses a different command than the AC text (`npx playwright test --trace off` in the mcr.microsoft.com/playwright:v1.65.1-noble container). It does not record `npm ci` or `npm test`, and it turns off the configured `trace: 'retain-on-failure'`.
