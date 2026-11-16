---
id: D07
question: Q07
status: decided
method: majority
class: local
votes: { pragmatist: A, architect: C, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q07.r1.tally.md]
adr: 
---
## Decision
Use A: the NVD API key is optional. The plugin reads it from the `NVD_API_KEY` environment variable when it is set,
and runs without a key otherwise (slower, throttled). Never put the key in the pom or in any committed file. Turn off
the Sonatype OSS Index analyzer and all Node/npm analyzers (Node package, Node audit, Yarn audit, pnpm audit, RetireJS).
Also turn off the .NET assembly analyzer. The factory run proves `mvn dependency-check:check` without a key.

## Why
- B is out: it needs a human-only key that this run does not have, so the checklist item (implementation-plan.md:1729) cannot pass.
- Conventions forbid committed secrets (conventions.md:12-13). An env-var-only key satisfies this.
- Q12 (answered A) limits the audit to Maven dependencies, so the Node/RetireJS analyzers stay off.
- OSS Index now needs credentials and fails the run without them, so it must be off by default.
- C adds two env vars and a conditional property that nothing in this run can test (minimal-implementation.md). The majority (0.8 mean) chose A.

## Rejected
- B: needs a human-supplied NVD key; the checklist item cannot pass in this run.
- C: an opt-in OSS Index path with no way to test it here; speculative scope. A human can add it later in a few lines.

## Dissent
Architect preferred C for a free upgrade path to OSS Index data. Risk to watch for A: the keyless first NVD download is
slow and can fail on HTTP 403/429. If it does not finish reliably, report it; do not commit a key and do not weaken
`failBuildOnCVSS`.

## Implementation notes
- `pom.xml`, `org.owasp:dependency-check-maven` `<configuration>` (pin a current version in a `<properties>` entry):
  - Key: prefer `<nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>` if the pinned version
    supports it; it reads the variable only when it is set. If you use `<nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>`
    instead, make sure an unset variable does not pass the literal text `${env.NVD_API_KEY}` as the key (for example a
    `<properties>` default of empty that the env value overrides). Verify by running once with the variable unset.
  - `ossindexAnalyzerEnabled=false`, `nodeAnalyzerEnabled=false`, `nodeAuditAnalyzerEnabled=false`,
    `yarnAuditAnalyzerEnabled=false`, `pnpmAuditAnalyzerEnabled=false`, `retireJsAnalyzerEnabled=false`,
    `assemblyAnalyzerEnabled=false`.
  - Throttle settings for the keyless path: a raised `nvdApiDelay` (for example 6000+ ms without a key) and
    `nvdMaxRetryCount`; keep the default data directory in `~/.m2` so the feed is cached between runs.
  - Invocation, binding, `failBuildOnCVSS` and the suppression file follow D06 (Q06).
- `env.example`: add `NVD_API_KEY=` (empty) with a comment: optional, free from nvd.nist.gov, speeds up the
  dependency check; never commit a value.
- `docs/project/deployment.md`: document `NVD_API_KEY` (optional; first run without it takes long and may be
  throttled), and state that OSS Index and the Node/RetireJS analyzers are off and why (Q12: Maven dependencies only).
- Proof: run `mvn dependency-check:check` once without a key and record the result in the ticket notes.
