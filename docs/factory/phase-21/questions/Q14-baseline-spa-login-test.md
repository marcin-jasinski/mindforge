---
id: Q14
status: decided
decision: D14
impact: low
blocks: [W13]
---
## Question
Does Phase 21 make `ApiFlowTest.theLoginRouteIsTheSinglePageApp` pass without the SPA build, and if so how?

## Context
The factory gate runs `mvn -B verify -DskipFrontend=true`; `target/classes/static/` is then absent, `SpaConfig`
(`src/main/java/dev/mindforge/api/config/SpaConfig.java`) finds no `index.html`, and the test
(`src/test/java/dev/mindforge/integration/api/ApiFlowTest.java:344`) fails. It has been the one baseline failure in
every factory run since Phase 17 (see `docs/factory/phase-17/REPORT.md`, `phase-20/REPORT.md`). CI builds the SPA and
passes. The checklist "CI passes on a clean checkout" is met either way; the question is whether the local gate stays
red.

## Known options
- A: Add a minimal `src/test/resources/static/index.html` fixture holding `<app-root>`, so the test checks the SPA
  fallback routing, not the Angular build. (Test classpath shadows a real build in tests only.)
- B: Make the test skip with a JUnit assumption when `classpath:/static/index.html` is absent.
- C: Leave it; document that the full gate needs `-DskipFrontend=false`.

## Constraints
- CLAUDE.md § Testing; `docs/standards/testing/test-writing.md`.
- The test's intent: `/login` returns the SPA, not Spring Security's default form ("doesNotContain Please sign in").
