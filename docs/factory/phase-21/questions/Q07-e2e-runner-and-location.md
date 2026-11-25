---
id: Q07
status: decided
decision: D07
impact: high
blocks: [W10, W11, W12]
---
## Question
Which Playwright binding runs the E2E, and where does the suite live: Playwright Test (Node/TypeScript) in
`src/test/e2e/` as the plan says, or Playwright for Java as JUnit tests in `src/test/java/dev/mindforge/e2e/`?

## Context
Plan 21.2 names the path `src/test/e2e/`. `docs/standards/testing/test-writing.md` § Test Discovery and Structure
defines `src/test/java/.../e2e/` — "full stack; `@Tag("e2e")`" — and `pom.xml` failsafe already includes
`**/e2e/**/*Test.java`. `frontend/package.json` has vitest only, no Playwright. `docs/project/tech-stack.md` § CI/CD
says "Maven verify + Angular test + Playwright E2E" without naming a binding.
- Node binding: a second `package.json` (or new devDependencies in `frontend/`), `npx playwright install`, a TS spec;
  runs outside Maven; fits a compose-based stack (Q06 A/B).
- Java binding: `com.microsoft.playwright:playwright` test dependency; JUnit + AssertJ; runs under failsafe, so it would
  run inside the factory gate unless excluded (Q08); fits Q06 C.

## Known options
- A: `@playwright/test` in `src/test/e2e/` with its own `package.json`/lockfile (npm@11), run against compose.
- B: `@playwright/test` added to `frontend/` devDependencies, specs in `frontend/e2e/`; the plan path is updated.
- C: Playwright for Java in `src/test/java/dev/mindforge/e2e/`, `@Tag("e2e")`, AssertJ.

## Constraints
- `docs/standards/global/conventions.md`: npm@11 is the only frontend package manager; minimal dependencies.
- CLAUDE.md § Testing: AssertJ for Java assertions; `@Testcontainers` for anything that needs PostgreSQL.
- The factory gate `mvn -B verify -DskipFrontend=true` builds no SPA, so a browser test inside `verify` cannot pass
  there unless it is excluded or the SPA is built.
- Whichever path wins, the plan text or `test-writing.md` is updated so they agree.
