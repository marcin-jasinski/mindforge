---
id: P21-01
title: Serve a test-classpath SPA shell so the login-route test passes without the Angular build
status: done
plan_tasks: [21.1]
decisions: [D14]
depends_on: []
size: S
issue: 66
commit: ffe473d
fix_rounds: 0
---
## Goal
`ApiFlowTest.theLoginRouteIsTheSinglePageApp` passes under the factory gate `mvn -B verify -DskipFrontend=true`
(no SPA build) and still passes with the full build. This removes the one baseline failure of the phase
(checklist item "CI passes on a clean checkout").

## Context
- `src/main/java/dev/mindforge/api/config/SpaConfig.java` serves `index.html` from `classpath:/static/` and returns
  null when it is absent (lines ~20, ~35). Under `-DskipFrontend=true` nothing is copied to `static/`
  (`pom.xml` ~378–391), so the test fails because of the environment, not a bug.
- `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java` (~344–349): `/login` must return the SPA shell
  (`<app-root>`), not Spring Security's login form.
- The real marker is in `frontend/src/index.html:11` (`<app-root></app-root>`).
- Journal baseline: "baseline failures: ApiFlowTest.theLoginRouteIsTheSinglePageApp".

## Decisions to apply
**D14 — Decision:** Use A: add a minimal test fixture `src/test/resources/static/index.html` that holds
`<app-root></app-root>`, so `ApiFlowTest.theLoginRouteIsTheSinglePageApp` checks the SPA fallback routing in
`SpaConfig` and passes under the factory gate `mvn -B verify -DskipFrontend=true` without the Angular build.

**Implementation notes:**
- Create `src/test/resources/static/index.html`, content only:
  `<!doctype html><html><head><title>MindForge test fixture</title></head><body><app-root></app-root></body></html>`
  Add an HTML comment `<!-- test fixture: shadows the real SPA on the test classpath; see D14 -->`. No script tags.
- Do not change `ApiFlowTest` assertions or `SpaConfig`.
- Check: `mvn -B verify -DskipFrontend=true` runs `theLoginRouteIsTheSinglePageApp` green; with a full build
  (`-DskipFrontend=false`) it stays green.
- Check that the packaged JAR does not contain the fixture (`jar tf target/*.jar | grep static/index.html` shows only
  the real build, or nothing under `-DskipFrontend=true`).

## Acceptance criteria
- [x] AC1: `src/test/resources/static/index.html` exists with exactly the D14 content (the comment, `<app-root></app-root>`, no `<script>`).
- [x] AC2: `mvn -B verify -DskipFrontend=true` is green, and `ApiFlowTest.theLoginRouteIsTheSinglePageApp` runs and passes (not skipped).
- [x] AC3: `ApiFlowTest` and `SpaConfig` are unchanged.
- [x] AC4: After `mvn -B package -DskipFrontend=true -DskipTests`, `jar tf target/*.jar | grep static/index.html` prints nothing (the fixture is not in the product JAR).

## Test plan
- Integration (existing): `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java` proves AC2 under the gate.
- AC4: a manual `jar tf` check by the implementer; record the output in Notes.

## Files likely touched
- `src/test/resources/static/index.html` (new)

## Out of scope
- Any change to `SpaConfig`, `ApiFlowTest`, the pom frontend profile, or the Angular build.
- Assumption-skips or `@Disabled` on the test.

## Notes
- Decided: put the D14 comment on its own line after the doctype, because a comment before the doctype can switch browsers to quirks mode.
- AC2: `mvn -B -o verify -DskipFrontend=true -Dit.test=ApiFlowTest` -> ApiFlowTest Tests run: 15, Failures: 0, Errors: 0, Skipped: 0; theLoginRouteIsTheSinglePageApp ran and passed.
- AC4: `mvn -B package -DskipFrontend=true -DskipTests` then `jar tf target/mindforge-1.1.0-SNAPSHOT.jar | grep static/index.html` printed nothing.
- Not run: the full build with `-DskipFrontend=false` (needs the Angular build); test-classes come first on the test classpath, so the fixture shadows the real index.html there.
