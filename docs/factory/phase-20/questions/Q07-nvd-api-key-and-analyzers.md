---
id: Q07
status: decided
decision: D07
impact: medium
blocks: [W07]
---
## Question
How does the dependency check get its vulnerability data: with or without an NVD API key (and from where), and which
analyzers are on (Sonatype OSS Index needs credentials; the Node/npm analyzers and the RetireJS analyzer would scan
`frontend/`)?

## Context
- `dependency-check-maven` (current major versions) downloads the NVD feed through the NVD API 2.0. Without an API key
  NVD throttles hard: the first update takes a long time and can fail on HTTP 403/429; with a key it takes minutes.
  The key is free but tied to a person's email — only the human can request it. No `NVD_API_KEY` is set on the
  factory host; the NVD endpoint is reachable from it.
- Sonatype OSS Index now requires a username/token; without them the OSS Index analyzer fails the run unless it is
  disabled.
- The plugin also has Node audit / RetireJS / Yarn analyzers; the plan names only Maven (Q12).
- A key must never be committed: it comes from an env var or `~/.m2/settings.xml` server entry.

## Known options
- A: No key required: the plugin reads `${env.NVD_API_KEY}` when set (documented), runs without it otherwise (slow);
  OSS Index and the Node/RetireJS analyzers disabled. The factory run proves the check without a key.
- B: As A, but require the key (the build fails fast without it) — needs the human to supply the key before the run
  can pass (would become a blocker).
- C: As A, plus OSS Index enabled when `OSSINDEX_USERNAME`/`OSSINDEX_TOKEN` are set.

## Constraints
- No secrets in the repository (`conventions.md` § environment variables; CLAUDE.md security rules).
- The checklist item `mvn dependency-check:check` passes must be achievable in this run.
- New env vars documented in `deployment.md`/`env.example`.
