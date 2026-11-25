---
id: D03
question: Q03
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q03.r1.tally.md]
adr: 
---
## Decision
Use A: bind `maven-checkstyle-plugin` goal `check` to the `validate` phase and `spotbugs-maven-plugin` goal `check` to
the `verify` phase in `pom.xml`. CI keeps one `mvn -B verify` step; the lifecycle gives the order Checkstyle ->
compile -> tests -> SpotBugs -> JaCoCo check. Keep the `-Dcheckstyle.skip` / `-Dspotbugs.skip` escapes and document them.

## Why
- CI has one `mvn -B verify` step (.github/workflows/ci.yml:25) and the JaCoCo check is already lifecycle-bound
  (pom.xml:436-454); A follows the same pattern and needs no CI change.
- The factory gate is `mvn -B verify -DskipFrontend=true` (.claude/factory.md). Only a lifecycle binding makes the
  factory gate and local `verify` equal to CI.
- All three ballots chose A with a mean confidence of 0.78.
- Build plugins are not a runtime dependency and touch no layer boundary, API, data model or security, so the class is local.

## Rejected
- B: verified hard objection — local and factory gates would pass while CI fails.
- C: duplicates the checks in CI (about one more minute for SpotBugs) only for log readability; it can be added later.

## Dissent
None. Risks to watch: validate-bound Checkstyle runs on the Windows `mvn -B package` leg with CRLF checkouts, and in
the offline fast loop (`-o`) it fails if the plugin was never downloaded.

## Implementation notes
- `pom.xml`: `maven-checkstyle-plugin` execution `checkstyle-check`, phase `validate`, goal `check`,
  `failOnViolation` true; `spotbugs-maven-plugin` execution `spotbugs-check`, phase `verify`, goal `check`,
  `failOnError` true. Declare SpotBugs above `jacoco-maven-plugin` so that in `verify` SpotBugs runs before
  `jacoco-check` (same-phase executions run in POM order). Pin both plugin versions.
- Checkstyle rules must be line-ending neutral (no `NewlineAtEndOfFile` with a fixed `lineSeparator`, no
  `RegexpMultiline` on `\n`), so the Windows leg passes. Verify against a CRLF checkout or set `lineSeparator=lf_cr_crlf`.
- A SpotBugs exclude file, if any, gives one reason per exclusion.
- Offline: the first online `gate` run downloads both plugins and their dependencies; after that `fast` (`-o`) works.
  Do not change the `fast` command unless Checkstyle makes it measurably slower.
- `docs/project/deployment.md` § Build: state that `mvn test` and `mvn package` also run Checkstyle (validate), that
  `mvn verify` also runs SpotBugs before the coverage gate, and document `-Dcheckstyle.skip` / `-Dspotbugs.skip`.
- Tests: `mvn -B verify -DskipFrontend=true` passes on a clean tree; a deliberate Checkstyle violation fails the
  build at validate (check by hand, do not commit it).
