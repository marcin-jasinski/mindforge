---
id: D07
question: Q07
status: decided
method: debate
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: { pragmatist: C->A, architect: C->A, skeptic: C->A }
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q07.r1.tally.md, docs/factory/phase-21/ballots/Q07.r2.tally.md]
adr:
---
## Decision
Use A: write the browser E2E with Playwright Test (`@playwright/test`, TypeScript) as its own npm@11 project in
`src/test/e2e/`, with its own `package.json` and committed `package-lock.json`. Run it against
`docker compose -f compose.yml -f compose.e2e.yml up --wait` (D06), outside Maven (D08). Keep the plan path. Edit
`test-writing.md` so the browser suite and the Java failsafe `e2e/` layer are two different, named things.

## Why
- D08:16, 39-40 forbids a suite in `src/test/java` or bound to `mvn verify`, which removes C (round-1 tally).
- D06:46-47, 52 puts the WireMock mappings in `src/test/e2e/model-stub/`. A keeps the specs and the answers they depend
  on in one tree. B splits them between `frontend/e2e/` and `src/test/e2e/`.
- A matches plan 21.2 as written (implementation-plan.md:1784). B needs a plan edit.
- B adds a test tool to the product build: Dockerfile:6-8 runs `npm ci` on the frontend lockfile, and the `frontend/**`
  extra gate (.claude/factory.md:35-36) would see the specs. A touches neither.
- The cost of A (a second lockfile) is soft: conventions.md:15-16 says "keep lean, document why"; it does not forbid it.
- Class `local`: a test-only dev dependency outside the product build; no runtime dependency, API, data model or layer
  change. The provider choice already has its ADR (0042, D06).

## Rejected
- B: same binding, but it couples a test tool to the frontend lockfile, the image build stage and the frontend gate,
  and it splits the suite from its D06 mappings.
- C: contradicts D08 (eliminated in round 1).

## Dissent
None in round 2. Round-1 view (C) was the reuse of `StubAIGateway` and one toolchain; that premise is gone after D06/D08.
Risk to watch: no factory gate covers `src/test/e2e/**`, so a TypeScript error shows only in the CI `e2e` job; and
`src/test/e2e/node_modules`/reports enter the Docker build context unless `.dockerignore` is updated.

## Implementation notes
- `src/test/e2e/package.json`: `"private": true`, `"packageManager": "npm@11.9.0"`, one devDependency
  `@playwright/test` with an exact pin (no caret), script `"test": "playwright test"`. Commit
  `src/test/e2e/package-lock.json`. Do not add Playwright to `frontend/package.json`.
- `src/test/e2e/tsconfig.json` of its own (strict); nothing in `frontend/tsconfig*.json` references it.
- `src/test/e2e/playwright.config.ts`: `baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080'`, Chromium only,
  `workers: 1`, `retries: process.env.CI ? 1 : 0`, `trace: 'retain-on-failure'`, reporter `html` (`open: 'never'`) to
  `playwright-report/`, output `test-results/`.
- Specs: `src/test/e2e/specs/smoke.spec.ts` (W10) and `src/test/e2e/specs/journey.spec.ts` (W11). Journey in plan 21.2
  order: register -> upload -> run completes -> pages appear -> open a page -> review flashcards -> take a quiz ->
  export bundle (validates). Use a unique e-mail per run. Assert that no reference-answer text is visible in the DOM
  (CLAUDE.md § Security). First assertion: an Angular-rendered element on the login page, not only `<app-root>`.
- No file in `src/test/e2e/` may match `*Test.java`; Maven must not see the suite. Check:
  `mvn -B verify -DskipFrontend=true` stays green (D08).
- `.dockerignore`: add `src/test/e2e/node_modules`, `src/test/e2e/test-results`, `src/test/e2e/playwright-report`.
  `.gitignore`: add `src/test/e2e/test-results/` and `src/test/e2e/playwright-report/`.
- CI `e2e` job (W12, D08): `actions/setup-node@v4` node 22, `cache-dependency-path: src/test/e2e/package-lock.json`;
  in `working-directory: src/test/e2e`: `npm ci`, `npx playwright install --with-deps chromium`, `npm test`. Upload
  `playwright-report/` and `test-results/` and dump WireMock unmatched requests with `if: always()`.
- Docs: `docs/standards/testing/test-writing.md` § Test Discovery and Structure keeps the three Java lines and adds
  `src/test/e2e/ — browser E2E (Playwright Test, TypeScript) against compose; not run by Maven; CI e2e job`.
  Plan 21.2: keep the path; change "stub-provider profile" to the `compose.e2e.yml` override (D06).
  `docs/project/tech-stack.md`: name the binding `@playwright/test` and why (Minimal Dependencies).
  `docs/project/deployment.md` "Run the E2E locally" (D08) uses the same commands.
