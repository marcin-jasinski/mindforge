# Factory run report — Phase 20 (Security Hardening)

- **Run:** phase-20 · started 2026-11-16T17:13 · finished 2026-11-16T21:17
- **Outcome:** all 8 tickets done on `factory/phase-20`. Not merged yet. Merge into `develop` waits for a human.
- **Branch:** `factory/phase-20` · base ca220ea

## Audit first

1. **ADR 0041 and ADR 0008.** CORS now admits one origin from `CORS_ALLOWED_ORIGIN` and none by default. ADR 0008 (Vercel split) is still "accepted". A human must decide whether to supersede it.
2. **Open GitHub issues #63 and #64.** P20-08 opened them. A human must triage them.
3. **Judge decision D11.** The judge decided D11 (trace record) from one ballot. The decision was low-risk. Check that the "Enforced by:" lines in `docs/standards/security/web-security.md` are what you want.
4. **D14 was raised during the build.** P20-06 found a gap: `RevertService.revert` did not return 404 for a run id that belongs to no run in the knowledge base. D14 (unanimous) fixed it in production code, under plan task 20.1. This is the only production change in a test ticket.
5. **Dependency upgrades in P20-07.** Spring Boot 4.1.0 to 4.1.2 and Spring AI 2.0.0 to 2.0.2 cleared the CVSS 7.0 findings. One suppression remains (Kotlin, CVE-2026-53914). Its regex matches all versions. See Deferred findings.

## Tickets

| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P20-01 | Limit sign-in and sign-up attempts per client address with a Caffeine fixed window | done | 5b1ca3b | 0 | D01, D03 |
| P20-02 | Take the client address from Tomcat's RemoteIpValve trusting only private-range proxies | done | 81678af | 0 | D02 |
| P20-03 | Run exactly one password hash comparison on every sign-in attempt | done | 5b63ddd | 0 | D04 |
| P20-04 | Register a CORS source that admits one origin from CORS_ALLOWED_ORIGIN and none by default | done | ebafc64 | 1 | D05 |
| P20-05 | Guard the wiki store's knowledge-base binding and the exporter's freedom from study data with tests | done | ba5f80a | 0 | Test only. The audit found no tenancy or export gap. |
| P20-06 | Prove that a tampered session token is refused and that another user's pages, runs and export are forbidden | done | ede4964 | 0 | D14 fix in `RevertService` |
| P20-07 | Declare the OWASP dependency check and clear every finding with CVSS 7.0 or higher | done | f9f4437 | 0 | Boot 4.1.2, Spring AI 2.0.2, 1 suppression. Keyless check exit 0. |
| P20-08 | Trace every web-security requirement to code and tests, document the new settings and ADRs, and close Phase 20 | done | d616870 | 0 | Docs only. Issues #63 and #64 opened. |

No ticket is parked.

## Decisions

| Id | Question | Method | Class | Decision (one line) | Dissent / risk to watch |
|---|---|---|---|---|---|
| D01 | Q01 auth rate limit key | majority | adr | Key the limiter by client address only (ADR 0038). | Skeptic chose B. Users behind one NAT share one allowance. |
| D02 | Q02 client address behind proxy | majority | adr | Use `forward-headers-strategy: native` and the default private-range proxies (ADR 0039). | Architect chose B. A proxy outside the range falls to the shared bucket. The live proxy is unverified. |
| D03 | Q03 auth limit budget and settings | unanimous | local | `mindforge.rate-limits.auth.requests` = 10, `.window` = PT1M. One bucket for login and register. | None. |
| D04 | Q04 sign-in timing and enumeration | unanimous | adr | One hash comparison on every attempt, with a dummy hash (ADR 0040). | Register `409 EMAIL_TAKEN` stays as an accepted risk, bounded by the rate limit. |
| D05 | Q05 CORS configuration | unanimous | adr | One origin from `CORS_ALLOWED_ORIGIN`, none by default (ADR 0041). | ADR 0008 supersession is left to a human. |
| D06 | Q06 dependency check invocation | majority | local | Declare the plugin with no bound execution. Run it by explicit goal. | Skeptic chose D (CI job). Phase 21 owns the CI shape. |
| D07 | Q07 NVD key and analyzers | majority | local | NVD key optional from `NVD_API_KEY`. OSS Index and Node analyzers off. | Architect chose C. The first keyless run is slow. |
| D11 | Q11 security trace record | judge | local | Add one "Enforced by:" line per section of `web-security.md`. | Judge call from one ballot (pragmatist). Audit first. |
| D14 | Q14 revert of a foreign run | unanimous | local | `RevertService.revert` throws `NotFoundException("Run")` after `start()`. | None. |

Method counts: unanimous 4 · majority 4 · debate 0 · judge 1. Blockers: 0.

## Decision outcomes

| Decision | Method | Vote changes | Models (panel / judge) | Tickets applying it (status, fix rounds) | Findings / rulings citing it |
|---|---|---|---|---|---|
| D01 | majority | none | opus / opus | P20-01 (done, 0) | none cite it by id; P20-01 spec-security checked it, no finding |
| D02 | majority | none | opus / opus | P20-02 (done, 0) | none; P20-02 spec-security checked the log line, no finding |
| D03 | unanimous | none | sonnet / opus | P20-01 (done, 0) | none; P20-01 correctness confirmed the yml keys match |
| D04 | unanimous | none | sonnet / opus | P20-03 (done, 0) | none |
| D05 | unanimous | none | sonnet / opus | P20-04 (done, 1) | none cite it by id |
| D06 | majority | none | sonnet / opus | P20-07 (done, 0) | none |
| D07 | majority | none | sonnet / opus | P20-07 (done, 0) | none |
| D11 | judge | none | sonnet (one ballot) / opus | P20-08 (done, 0) | none |
| D14 | unanimous | none | opus / opus | P20-06 (done, 0) | P20-06 spec-security: AC4 + D14 verified, no finding |

## Panel statistics

Decisions per method: unanimous 4, majority 4, judge 1 (debate 0). Round-2 debates 0. Vote changes 0 (toward the final decision 0, away 0). Ballots discounted for false evidence 0. Disputed findings: none (no `rulings/` directory). Questions Q08, Q09, Q10, Q12 and Q13 were answered from the scope and needed no panel.

## ADRs written

- 0038 auth rate limit is keyed by client address (D01)
- 0039 client address comes from Tomcat's RemoteIpValve with private-range proxies (D02)
- 0040 password sign-in always runs one hash comparison (D04)
- 0041 CORS admits one origin from the environment and none by default (D05)

## Deferred findings

- P20-05 C1 (minor, 0.6) `ExportReadsNoStudyDataGuardTest.java:44`: the scan does not assert that it scanned the export files. It can pass while it checks nothing if the files move.
- P20-07 S1 (minor, 0.5) `dependency-check-suppressions.xml:11`: the Kotlin suppression matches all versions (`@.*$`). Pin to `@2\.3\.21$`.
- P20-08 (2 nits, correctness): not fixed.
- Holistic H1 (minor, 0.9) `api/config/RateLimitExceededException.java:3`: the Javadoc still says "model-calling requests" only. It now also covers sign-in and sign-up per client address.

## Manual checks still open

- The first keyless NVD download for `mvn dependency-check:check` takes about 30 minutes. Plan for it, or set `NVD_API_KEY`.
- The client-address behaviour behind the live proxy (Railway or Render) is not verified in production. Check that the limiter sees the real client address and not the proxy address.
- Decide whether to supersede ADR 0008.
- Triage issues #63 and #64.
- Phase 21 follow-up: a scheduled dependency-check job (D06).

## Statistics

Tickets done 8, parked 0. Total fix rounds 1 (P20-04). Disputed findings 0. Decisions: unanimous 4, majority 4, judge 1. Baseline failure in every gate: `ApiFlowTest.theLoginRouteIsTheSinglePageApp`.

Agent calls: 65 total. Forced continuations: 2. Stalled: 0.

| Role | Model | Calls |
|---|---|---|
| planner | opus | 1 |
| solver | opus | 9 |
| solver | sonnet | 12 |
| judge | opus | 9 |
| ticket-writer | opus | 1 |
| implementer | default | 9 |
| tester | sonnet | 4 |
| reviewer | opus | 13 |
| reviewer | sonnet | 5 |
| fixer | default | 1 |
| scribe | sonnet | 1 |
