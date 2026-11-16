# Phase 20 — Security Hardening: draft breakdown

Plan tasks: 20.1 (security checklist pass against `web-security.md`, incl. exporter / `WikiStore` `kbId` / per-KB
dedup; fix gaps), 20.2 (`dependency-check-maven`; fix or suppress `CVSS >= 7.0`), 20.3 (Caffeine limiter on login and
register; `429` + `Retry-After`), 20.4 (security integration tests: rate limit triggers, tampered JWT → 401, cross-user
pages/runs/export → 403), completion checklist (CL1: every requirement traced; CL2: `mvn dependency-check:check` passes;
CL3: limiter active on auth endpoints), and close-out docs. Tests ship with the item that adds the behaviour; W06
collects the 20.4 cases that need no new production code.

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | Client address behind the proxy per Q02 (`server.forward-headers-strategy` and trusted-proxy setting, or the chosen alternative); `AppProperties` validation if a setting is added; test: a forged `X-Forwarded-For` from an untrusted peer does not change the address the app sees, a trusted peer's header does | 20.3, CL3 | S | — | Q02 |
| W02 | Auth rate limiter per Q01/Q03: Caffeine fixed-window limiter keyed per Q01 on `POST /api/v1/auth/login` and `/register` (interceptor or filter registered in `RateLimitConfig` or its own `@Configuration`), new `AppProperties` settings, reuses `RateLimitExceededException` + handler (`429`, `Retry-After`, `RateLimit-*`, `RATE_LIMITED`); unit tests for the window/key logic without Spring; integration test: N+1th login/register → `429` with `Retry-After`, recovers after the window, another key unaffected; raise limits in `ApiFlowTest`/other ITs if needed | 20.3, 20.4 (rate limit), CL3 | M | W01 | Q01, Q02, Q03, Q08, Q09 |
| W03 | Sign-in timing per Q04: `AccountService.authenticate` compares against a dummy hash for an unknown email and an OAuth-only account (through `PasswordHasher`), removes the ponytail; unit test: hasher called once in all three failure cases, same `AccountException` | 20.1 | S | — | Q04 |
| W04 | CORS per Q05: `CorsConfigurationSource` bean from `CORS_ALLOWED_ORIGIN` (never `*`) and `.cors()` in `SecurityConfig`; test: preflight from the configured origin allowed with credentials, other origin refused. *If Q05 picks C, this item becomes docs-only and needs the human (ADR 0008).* | 20.1 | S | — | Q05 |
| W05 | Tenancy and export guards (20.1 named items): `WikiStoreAdapterTest` case that every read method with another knowledge base's id returns nothing; a guard test that the export code (`infrastructure/export`, `BundleQueryAdapter`) references no study entity/repository and `findLogEntries` selects no `cost`/`step_versions`/`failures`/`findings`; fix any gap the audit of the remaining `web-security.md` sections finds (re-run `OutboundHttpGuardTest`, forbidden-field check) | 20.1, CL1 | M | — | Q13 |
| W06 | Security integration tests (20.4 remainder): tampered / wrong-key / expired / `alg:none` JWT cookie → `401`; cross-user `403` for run report by id, revert, page revisions, export (gaps beyond `ApiFlowTest`); id-swap under own knowledge base → `404` with no foreign data | 20.4 | M | — | Q10 |
| W07 | OWASP: `dependency-check-maven` in `pom.xml` per Q06/Q07 (`failBuildOnCVSS=7`, `NVD_API_KEY` from env, unneeded analyzers off), `dependency-check-suppressions.xml` with a reason per entry; run `mvn dependency-check:check`, upgrade the dependencies with fixable `CVSS >= 7.0` findings (managed versions/properties), suppress the rest with justification; full gate green after upgrades | 20.2, CL2 | M–L | — | Q06, Q07, Q12 |
| W08 | Docs and close-out: the requirement → code → test trace per Q11; `web-security.md` (Slack downloader paragraph, auth limit, CORS per Q05); `api.md` § Rate Limit Headers (auth endpoints, key); `deployment.md` + `env.example` (auth limit vars, trusted proxies, `CORS_ALLOWED_ORIGIN`, `NVD_API_KEY`, how to run the OWASP check); ADRs for Q01+Q02 (auth limit key and client address) and Q05 if it changes ADR 0008; plan 20.x `[x]` with "As built" (path correction `/api/v1/auth`), roadmap, `docs/INDEX.md`, CLAUDE.md "Project State" | 20.1, CL1, close-out | S | W01–W07 | Q05, Q11, Q13 |

Notes:
- W01, W03, W04, W05, W06, W07 can start at once and in parallel; W02 waits for W01; W08 last.
- W07 adds a Maven plugin and may bump dependency versions: the offline `fast` command needs them resolved once online,
  and a version bump can break tests outside this phase's code — keep each bump small and run the full gate.
- W07's run time depends on Q07: without an NVD key the first database download is slow (tens of minutes).
- Coverage of plan tasks: 20.1 → W03, W04, W05, W08; 20.2 → W07; 20.3 → W01, W02; 20.4 → W02, W06.
