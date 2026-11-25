---
id: Q05
status: answered
impact: low
blocks: [W07]
answered_by: docs/project/deployment.md#Build and docs/wayfinder/review-tickets/R02-no-failsafe-plugin.md
---
## Question
Does CI run `mvn test` as the plan says, or `mvn verify`?

## Context
Plan 21.1 says "`mvn test` (Testcontainers)". Since R02, `mvn test` runs only `unit/` (surefire); the Testcontainers
`integration/` and `e2e/` tests run under failsafe in `mvn verify`, where the JaCoCo 70% check is also bound.

## Known options
- A: `mvn -B verify` (current CI). Runs Testcontainers and the coverage gate.
- B: `mvn test` — would skip every Testcontainers test and the gate.

## Constraints
`deployment.md` § Build: "`mvn verify` also runs the Testcontainers-backed `integration/` and `e2e/` tests (failsafe)
and enforces the 70% coverage gate". The plan's intent (Testcontainers + gate) is met only by A. Keep A.
