# Phase 20 — Security Hardening: tickets

Execution order. P20-01, P20-03, P20-04, P20-05 and P20-07 can start at once. P20-02 and P20-06 wait for P20-01
(P20-02 tests the limiter's buckets; P20-06 must set the new auth limit high). P20-08 is last.

| Id | Title | Depends on | Size | Status |
|---|---|---|---|---|
| [P20-01](P20-01-auth-rate-limiter.md) | Limit sign-in and sign-up attempts per client address with a Caffeine fixed window | — | M | ready |
| [P20-02](P20-02-client-address-behind-proxy.md) | Take the client address from Tomcat's RemoteIpValve trusting only private-range proxies | P20-01 | S | ready |
| [P20-03](P20-03-sign-in-runs-one-hash-comparison.md) | Run exactly one password hash comparison on every sign-in attempt | — | S | ready |
| [P20-04](P20-04-cors-one-origin-from-environment.md) | Register a CORS source that admits one origin from CORS_ALLOWED_ORIGIN and none by default | — | S | ready |
| [P20-05](P20-05-tenancy-and-export-guards.md) | Guard the wiki store's knowledge-base binding and the exporter's freedom from study data with tests | — | M | ready |
| [P20-06](P20-06-security-integration-tests.md) | Prove that a tampered session token is refused and that another user's pages, runs and export are forbidden | P20-01 | M | ready |
| [P20-07](P20-07-owasp-dependency-check.md) | Declare the OWASP dependency check and clear every finding with CVSS 7.0 or higher | — | L | ready |
| [P20-08](P20-08-security-docs-trace-and-close-out.md) | Trace every web-security requirement to code and tests, document the new settings and ADRs, and close Phase 20 | P20-01..P20-07 | M | ready |

## Coverage

- Plan tasks: 20.1 → P20-03, P20-04, P20-05, P20-08; 20.2 → P20-07; 20.3 → P20-01, P20-02; 20.4 → P20-01 (rate limit
  triggers), P20-06 (tampered JWT, cross-user); completion checklist → P20-08 (item 1), P20-07 (item 2), P20-01 (item 3).
- Decisions: D01 → P20-01 (docs in P20-08); D02 → P20-02 (docs in P20-08); D03 → P20-01 (docs in P20-08);
  D04 → P20-03 (docs in P20-08); D05 → P20-04 (docs in P20-08); D06 → P20-07 (docs and follow-up in P20-08);
  D07 → P20-07 (docs in P20-08); D11 → P20-08.
- Answered questions: Q08 → P20-01, P20-08; Q09 → P20-01; Q10 → P20-06; Q12 → P20-07; Q13 → P20-05, P20-08.
- Breakdown order changed: W02 (limiter) comes before W01 (client address), because the client-address tests observe
  the limiter's buckets; W01 is P20-02. All docs (including `env.example`) are in P20-08.
- Open for the human (not in any ticket): whether ADR 0008 is superseded (D05, ADR 0041).
