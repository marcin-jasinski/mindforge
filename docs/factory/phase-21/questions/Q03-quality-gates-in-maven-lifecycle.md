---
id: Q03
status: decided
decision: D03
impact: medium
blocks: [W05, W06, W07]
---
## Question
Are the Checkstyle and SpotBugs checks bound to the Maven lifecycle (so `mvn verify` runs them everywhere), or do they
run only as separate CI steps?

## Context
Plan 21.1 gives an order: Checkstyle → SpotBugs → tests → JaCoCo. Today CI runs one `mvn -B verify` step and the
JaCoCo check is bound to `verify` in `pom.xml`. The factory gate is `mvn -B verify -DskipFrontend=true`
(`.claude/factory.md`), and the fast loop is `mvn -q -o test ...`. If the checks are bound to `validate`/`verify`, the
factory and every developer run them locally and the CI order comes from the lifecycle; the build gets slower
(SpotBugs adds about a minute). If they are CI-only steps, local and factory gates can pass while CI fails. The Windows
leg (`mvn -B package`) is also affected by a `validate`-bound Checkstyle.

## Known options
- A: Bind `checkstyle:check` to `validate` and `spotbugs:check` to `verify`; CI keeps one `mvn -B verify` step (order
  by lifecycle: Checkstyle before compile, SpotBugs after tests, then JaCoCo check). A `-Dcheckstyle.skip` /
  `-Dspotbugs.skip` escape stays available.
- B: Declare the plugins without executions; CI runs `mvn -B checkstyle:check`, `mvn -B compile spotbugs:check`, then
  `mvn -B verify` as separate visible steps. Local `verify` does not run them.
- C: A as the source of truth, and CI also splits the steps for readable logs.

## Constraints
- `.claude/factory.md` commands: the gate must stay runnable offline after the first download (`fast` uses `-o`).
- `docs/project/deployment.md` § Build documents what `mvn test` / `mvn verify` run; it must be updated to match.
