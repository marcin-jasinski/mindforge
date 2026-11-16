---
id: Q06
status: decided
decision: D06
impact: medium
blocks: [W07]
---
## Question
Where and when does the OWASP dependency check run: only when called explicitly (`mvn dependency-check:check`), bound
into `mvn verify` (the factory gate and CI), in an opt-in Maven profile, or as its own CI job?

## Context
- Plan 20.2: "`dependency-check-maven`; fix or suppress any `CVSS >= 7.0` findings." Checklist: "`mvn
  dependency-check:check` passes." No phase/binding is named.
- `pom.xml` has no dependency-check plugin. CI (`.github/workflows/ci.yml`) runs `mvn -B verify` on linux and
  `mvn -B package` on windows. The factory `gate` is `mvn -B verify -DskipFrontend=true`; `fast` is offline (`-o`).
- The first run downloads the whole NVD dataset (hundreds of MB; tens of minutes without an API key, see Q07) and
  needs the network. Binding it to `verify` makes every gate run and every CI run slower and network-dependent, and a
  new CVE published overnight fails an unrelated build. Not binding it means no automated enforcement until Phase 21
  (CI) or a scheduled job.
- Phase 21.1 owns the CI pipeline shape ("Checkstyle → SpotBugs → mvn test → JaCoCo").

## Known options
- A: Declare the plugin in `<build><plugins>` with `failBuildOnCVSS=7` and the suppression file, no execution bound;
  run it explicitly (`mvn dependency-check:check`); document it in `deployment.md`/CONTRIBUTING-style docs.
- B: As A plus an execution bound to `verify` inside an opt-in profile (`-Powasp`).
- C: Bound to `verify` always.
- D: As A, plus a scheduled/manual GitHub Actions job that runs it with an NVD key secret.

## Constraints
- Checklist: `mvn dependency-check:check` passes (must work with the explicit goal).
- Factory gate and integration tests must keep running (`.claude/factory.md` commands).
- CI shape is Phase 21's (out of scope unless chosen here).
- `minimal-implementation.md`.
