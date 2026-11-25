---
id: D14
question: Q14
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q14.r1.tally.md]
adr:
---
## Decision
Use A: add a minimal test fixture `src/test/resources/static/index.html` that holds `<app-root></app-root>`, so
`ApiFlowTest.theLoginRouteIsTheSinglePageApp` checks the SPA fallback routing in `SpaConfig` and passes under the
factory gate `mvn -B verify -DskipFrontend=true` without the Angular build.

## Why
- `SpaConfig` serves `index.html` from `classpath:/static/` and returns null when it is absent (SpaConfig.java:20,35);
  under `-DskipFrontend=true` nothing is copied there (pom.xml:378-391), so the test is red by environment, not by bug.
- The test intent is routing: `/login` must return the SPA shell and not Spring Security's form (ApiFlowTest.java:344-349).
  A fixture keeps that assertion live in every run; B would hide it in the gate, C keeps the gate red.
- The fixture marker equals the real one (frontend/src/index.html:11), so the test means the same with or without a build.
- Shadow risk (Q07 skeptic): test-classes precede classes on the test classpath, so the fixture shadows the real
  `index.html` for any in-JVM test. D08 places the E2E outside `src/test/java`, against the compose image built from
  the main JAR, which never holds test resources. No present test is affected.

## Rejected
- B: an assumption skip makes the test a no-op in the gate, which is where it fails; the routing check is then lost locally.
- C: leaves the one baseline failure in place for every factory run.

## Dissent
None (single ballot). Risk to watch: if a later decision puts a browser E2E inside `src/test/java` with
`@SpringBootTest`, the fixture shadows the real SPA; that E2E then fails loudly (no scripts load), and the fixture
must move (for example, served only to ApiFlowTest) at that time.

## Implementation notes
- Create `src/test/resources/static/index.html`, content only:
  `<!doctype html><html><head><title>MindForge test fixture</title></head><body><app-root></app-root></body></html>`
  Add an HTML comment `<!-- test fixture: shadows the real SPA on the test classpath; see D14 -->`. No script tags.
- Do not change `ApiFlowTest` assertions or `SpaConfig`.
- Check: `mvn -B verify -DskipFrontend=true` runs `theLoginRouteIsTheSinglePageApp` green; with a full build
  (`-DskipFrontend=false`) it stays green.
- Check that the packaged JAR does not contain the fixture (`jar tf target/*.jar | grep static/index.html` shows only
  the real build, or nothing under `-DskipFrontend=true`).
