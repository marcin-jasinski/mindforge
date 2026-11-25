---
id: D08
question: Q08
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q08.r1.tally.md]
adr:
---
## Decision
Use A: add a separate `e2e` job to `.github/workflows/ci.yml` that runs on every push and PR (the existing
`push:`/`pull_request:` triggers), `needs: linux`, `runs-on: ubuntu-latest`. The job builds the image with compose,
starts the stack, and runs the browser suite outside Maven. Do not bind the suite to `mvn verify` in any profile.

## Why
- ci.yml triggers on unfiltered `push:` and `pull_request:` (ci.yml:3-5); A adds a job and no new trigger concept.
- tech-stack.md:189 requires Playwright E2E on every PR; A meets it and also catches breaks on factory branch pushes.
- The factory gate `mvn -B verify -DskipFrontend=true` (.claude/factory.md) has no SPA; a suite outside Maven leaves
  it untouched, which is the first constraint.
- The Windows leg has no Linux Docker; `needs: linux` keeps the job on Linux and skips it when the build is red.
- All three lenses agree at 0.75; no verified hard objection against A.

## Rejected
- B: adds a branch filter nothing else in ci.yml uses; feature-branch breaks show up only at PR time.
- C: puts Docker image and browser work inside Maven and adds a profile that the gate and CI must keep apart.

## Dissent
None. Shared risk: a flaky E2E turns every push red. Watch it; if minutes or flakiness become a real cost, narrow the
trigger to B (one-line change).

## Implementation notes
- Job `e2e` in `.github/workflows/ci.yml`: `needs: linux`, `runs-on: ubuntu-latest`; `docker compose build`,
  `docker compose up -d --wait`, run the suite, upload the report and traces with `if: always()`,
  `docker compose down -v` with `if: always()`. Pin the browser version. The runner and the stub provider come from
  D06/D07; this decision fixes only where and when it runs.
- Place the suite outside `src/test/java`. pom.xml:330-333 makes failsafe include `**/e2e/**/*Test.java`, so a suite
  inside the Maven test tree under an `e2e` package would be bound to `verify` and break the gate.
- `docs/project/deployment.md`: add "Run the E2E locally" with the exact compose and suite commands. State that
  `mvn verify` and the factory gate do not run it. Keep it separate from the existing "e2e/ tests (failsafe)" text
  (deployment.md:15-16) so the two cannot be confused.
- Check: `mvn -B verify -DskipFrontend=true` stays green with the suite present.
