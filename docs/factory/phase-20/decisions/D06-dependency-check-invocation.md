---
id: D06
question: Q06
status: decided
method: majority
class: local
votes: { pragmatist: A, architect: A, skeptic: D }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q06.r1.tally.md]
adr:
---
## Decision
Use A: declare `org.owasp:dependency-check-maven` in `pom.xml` `<build><plugins>` with `failBuildOnCVSS=7` and the suppression file, with no `<executions>` bound to any phase. Run it only by the explicit goal `mvn dependency-check:check`. Document the command in `docs/project/deployment.md` and record that automated enforcement (scheduled or CI job) belongs to Phase 21.

## Why
- Checklist needs only `mvn dependency-check:check` to pass (implementation-plan.md:1729); 20.2 names no binding (1717-1718).
- The factory gate and CI both run `verify` (.claude/factory.md:33, ci.yml:25); a binding there adds network/NVD dependence and unrelated-CVE failures.
- Phase 21.1 owns the CI pipeline shape (implementation-plan.md:1742-1743); a new workflow now pre-empts it.
- Smallest change that meets the checklist (minimal-implementation.md); the pom config is reused unchanged by any later job.

## Rejected
- B: opt-in profile adds a concept nobody runs; same enforcement as A with more config.
- C: verified hard objection - makes gate and CI network-dependent and fails unrelated builds on new CVEs.
- D: adds a workflow file in Phase 20 when Phase 21 owns CI shape; also needs an NVD secret set up by a human.

## Dissent
Skeptic (D): with no automated run, the check can rot and a new high CVE goes unseen until someone runs it. Watch: Phase 21 must add a scheduled/manual job with the NVD key; record this as a follow-up.

## Implementation notes
- `pom.xml`: version property `dependency-check-maven.version`; plugin in `<build><plugins>`; configuration `failBuildOnCVSS` = 7, `suppressionFiles` -> `dependency-check-suppressions.xml` at repo root (each suppression with a `<notes>` reason). NVD key and analyzer settings follow D07.
- No `<executions>` block. Verify: `mvn -B verify -DskipFrontend=true` must not start the dependency check (no NVD download in gate logs).
- `docs/project/deployment.md` (or the security/ops section): how to run `mvn dependency-check:check`, first-run cost, where suppressions live.
- Add a follow-up for Phase 21: scheduled + `workflow_dispatch` dependency-check job using an `NVD_API_KEY` secret.
