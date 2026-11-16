---
id: P20-07
title: Declare the OWASP dependency check and clear every finding with CVSS 7.0 or higher
status: ready
plan_tasks: [20.2]
decisions: [D06, D07]
depends_on: []
size: L
issue: 61
commit:
fix_rounds: 0
---
## Goal
`pom.xml` declares `org.owasp:dependency-check-maven` with `failBuildOnCVSS=7`, a suppression file, the NVD API key
read only from the optional `NVD_API_KEY` environment variable, and the OSS Index, Node/npm/Yarn/pnpm, RetireJS and
.NET analyzers off. It is bound to no phase. `mvn dependency-check:check` passes without an NVD key: every
`CVSS >= 7.0` finding is fixed by a dependency upgrade or suppressed with a reason. The full gate stays green.

## Context
- `pom.xml` has no `dependency-check-maven`; there is no suppression file and no `NVD_API_KEY` in the environment. The
  NVD API is reachable from the factory host.
- Gates: `fast` = `mvn -q -o test -DskipFrontend=true -Djacoco.skip=true` (offline: the new plugin must be resolved
  online once, for example by the first `mvn dependency-check:check` run); `gate` = `mvn -B verify -DskipFrontend=true`.
  `.github/workflows/ci.yml` runs `mvn -B verify` (linux) and `mvn -B package` (windows); do not change CI (Phase 21).
- Q12 (answered A): the audit covers Maven dependencies only; no npm audit.
- Keyless first NVD download is slow (tens of minutes) and can be throttled (HTTP 403/429): run it with
  `run_in_background` / a long timeout. Keep the default data directory in `~/.m2` so the feed is cached.
- Dependency versions are managed by the Spring Boot 4.1 parent and `<dependencyManagement>` / `<properties>` in
  `pom.xml`. Prefer overriding a managed version property (for example `<tomcat.version>`) over adding explicit
  versions. Keep each bump small and run the full gate after bumps: a bump can break tests outside this phase.
- Binding rules: `docs/standards/global/minimal-implementation.md`; conventions forbid committed secrets.

## Decisions to apply
**D06 — Decision:** Use A: declare `org.owasp:dependency-check-maven` in `pom.xml` `<build><plugins>` with
`failBuildOnCVSS=7` and the suppression file, with no `<executions>` bound to any phase. Run it only by the explicit
goal `mvn dependency-check:check`. Document the command in `docs/project/deployment.md` and record that automated
enforcement (scheduled or CI job) belongs to Phase 21.

**D06 — Implementation notes:**
- `pom.xml`: version property `dependency-check-maven.version`; plugin in `<build><plugins>`; configuration
  `failBuildOnCVSS` = 7, `suppressionFiles` -> `dependency-check-suppressions.xml` at repo root (each suppression with
  a `<notes>` reason). NVD key and analyzer settings follow D07.
- No `<executions>` block. Verify: `mvn -B verify -DskipFrontend=true` must not start the dependency check (no NVD
  download in gate logs).
- (The `deployment.md` text and the Phase 21 follow-up issue are P20-08.)

**D07 — Decision:** Use A: the NVD API key is optional. The plugin reads it from the `NVD_API_KEY` environment variable
when it is set, and runs without a key otherwise (slower, throttled). Never put the key in the pom or in any committed
file. Turn off the Sonatype OSS Index analyzer and all Node/npm analyzers (Node package, Node audit, Yarn audit, pnpm
audit, RetireJS). Also turn off the .NET assembly analyzer. The factory run proves `mvn dependency-check:check` without
a key.

**D07 — Implementation notes:**
- `pom.xml`, plugin `<configuration>` (pin a current version in a `<properties>` entry):
  - Key: prefer `<nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>` if the pinned version
    supports it; it reads the variable only when it is set. If you use `<nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>`
    instead, make sure an unset variable does not pass the literal text `${env.NVD_API_KEY}` as the key (for example a
    `<properties>` default of empty that the env value overrides). Verify by running once with the variable unset.
  - `ossindexAnalyzerEnabled=false`, `nodeAnalyzerEnabled=false`, `nodeAuditAnalyzerEnabled=false`,
    `yarnAuditAnalyzerEnabled=false`, `pnpmAuditAnalyzerEnabled=false`, `retireJsAnalyzerEnabled=false`,
    `assemblyAnalyzerEnabled=false`.
  - Throttle settings for the keyless path: a raised `nvdApiDelay` (for example 6000+ ms without a key) and
    `nvdMaxRetryCount`; keep the default data directory in `~/.m2` so the feed is cached between runs.
- If the keyless download does not finish reliably, report it; do not commit a key and do not weaken
  `failBuildOnCVSS`.
- Proof: run `mvn dependency-check:check` once without a key and record the result in the ticket Notes.
- (`env.example` `NVD_API_KEY=` and the `deployment.md` text are P20-08.)

## Acceptance criteria
- [ ] AC1: `pom.xml` declares the plugin with a pinned version property, `failBuildOnCVSS` 7, the suppression file,
  the env-only NVD key, the seven analyzers off and keyless throttle settings, and no `<executions>`.
- [ ] AC2: `dependency-check-suppressions.xml` exists at the repo root; every `<suppress>` has a `<notes>` reason that
  names why the finding does not apply or cannot be fixed (for example not reachable, no fixed version, false CPE
  match), and suppresses by the narrowest selector (`packageUrl` + `cve`/`vulnerabilityName`).
- [ ] AC3: Every `CVSS >= 7.0` finding with a fixed version available inside the Spring Boot 4.1 line is fixed by an
  upgrade, not suppressed.
- [ ] AC4: `mvn dependency-check:check` with `NVD_API_KEY` unset exits 0; the result (duration, findings fixed,
  findings suppressed) is recorded in Notes.
- [ ] AC5: `mvn -B verify -DskipFrontend=true` does not run the dependency check and stays green (except the known
  baseline failure); no NVD key or secret is in any committed file.

## Test plan
- AC1, AC2, AC5 (no secret): review of `pom.xml` and the suppression file.
- AC3, AC4: run `mvn dependency-check:check` (online, long timeout, background) and record the report summary
  (`target/dependency-check-report.html`) in Notes.
- AC5: full gate `mvn -B verify -DskipFrontend=true`; then `mvn -q -o test -DskipFrontend=true -Djacoco.skip=true`
  still works offline after the plugin is resolved.

## Files likely touched
- `pom.xml`
- `dependency-check-suppressions.xml` (new, repo root)

## Out of scope
- A CI job, a scheduled workflow, or a phase binding (Phase 21); an npm / frontend audit (Q12).
- The OSS Index opt-in path; committing an NVD key; lowering `failBuildOnCVSS`.
- Docs (`deployment.md`, `env.example`) and the Phase 21 follow-up issue — P20-08.

## Notes
