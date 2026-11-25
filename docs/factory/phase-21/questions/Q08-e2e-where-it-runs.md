---
id: Q08
status: decided
decision: D08
impact: medium
blocks: [W10, W12]
---
## Question
Where does the E2E run: in a separate CI job on every push and PR, only on PRs and the integration branch, or also in
the local/factory `mvn verify` gate?

## Context
Plan 21.2 gives no trigger; tech-stack.md § CI/CD says "build + test on every PR (... Playwright E2E)". The E2E needs a
Docker image build (or the SPA build), browsers, and a few minutes. The factory gate (`.claude/factory.md`) is
`mvn -B verify -DskipFrontend=true` with no SPA, so an E2E bound to `verify` breaks it. The existing CI linux job
already builds the SPA through `mvn -B verify`; the Windows leg has no Linux Docker.

## Known options
- A: A separate `e2e` job in `ci.yml` on every push and PR (needs: linux), building the image with compose and running
  the suite; not part of `mvn verify` (excluded from failsafe or living outside Maven).
- B: Same job, but only on `pull_request` and pushes to `develop`.
- C: Part of `mvn verify` behind a Maven profile (`-Pe2e`) that CI enables and the factory gate does not.

## Constraints
- The factory gate must stay runnable and green without the SPA build.
- `docs/project/deployment.md` must state how to run the E2E locally.
