---
id: P20-02
title: Take the client address from Tomcat's RemoteIpValve trusting only private-range proxies
status: ready
plan_tasks: [20.3]
decisions: [D02]
depends_on: [P20-01]
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
`server.forward-headers-strategy: native` is set, so behind the Railway/Render proxy `request.getRemoteAddr()`,
the scheme and `isSecure()` come from `X-Forwarded-For` / `X-Forwarded-Proto` — but only when the direct peer is in
Spring Boot's default `server.tomcat.remoteip.internal-proxies` range. The auth limiter from P20-01 then has one bucket
per real client, and a forged `X-Forwarded-For` from an untrusted peer does not change the bucket.

## Context
- Production runs behind the Railway/Render proxy (`docs/project/deployment.md`). Today no
  `server.forward-headers-strategy` is set and nothing reads `X-Forwarded-For`, so `getRemoteAddr()` is the proxy and
  every user would share one auth bucket.
- P20-01 adds `dev.mindforge.api.config.AuthRateLimitInterceptor`, keyed by `request.getRemoteAddr()`, on
  `POST /api/v1/auth/login|register`, settings `mindforge.rate-limits.auth.requests|window`, and a 429 INFO log line
  with the key.
- `src/main/resources/application.yml`: `server:` block at line ~75 (`port`, `shutdown: graceful`).
- Spring Boot 4.1 / tomcat-embed-core 11.0.22: the default internal-proxies regex covers private (10/8, 172.16/12,
  192.168/16), CGNAT (100.64/10), loopback, link-local and IPv6 ULA ranges. `RemoteIpValve` walks XFF right to left
  and skips trusted hops. `setInternalProxies("")` trusts no peer in this version.
- MockMvc bypasses the valve: tests need a real Tomcat (`@SpringBootTest(webEnvironment = RANDOM_PORT)`). In tests the
  client connects from loopback, which is in the default trusted range.
- ADR 0039 (`docs/adr/0039-client-address-comes-from-tomcats-remote-ip-valve-with-private-range-proxies.md`) is already
  written; do not write a new ADR.
- Binding rules: CLAUDE.md § Testing (Testcontainers, AssertJ); `docs/standards/global/validation.md` (allowlist).

## Decisions to apply
**D02 — Decision:** Use A: set `server.forward-headers-strategy: native` in `application.yml` and keep Spring Boot's
default `server.tomcat.remoteip.internal-proxies` (private, CGNAT, loopback, link-local and IPv6 ULA ranges). Tomcat's
`RemoteIpValve` then sets `request.getRemoteAddr()`, the scheme and `isSecure()` from `X-Forwarded-For` /
`X-Forwarded-Proto` only when the direct peer is in that range. The auth limiter (D01) keys on `getRemoteAddr()` and no
application code reads `X-Forwarded-For`. Document the existing Spring variable `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`
as the optional operator override. Add no new `TRUSTED_PROXIES` setting.

**D02 — Implementation notes:**
- `src/main/resources/application.yml`, under `server:` (line 75): `forward-headers-strategy: native` with a one-line
  comment: "X-Forwarded-* trusted only from server.tomcat.remoteip.internal-proxies (Spring default: private ranges)".
  Do not set `server.tomcat.remoteip.internal-proxies` in `application.yml`.
- Limiter (D01): key is `request.getRemoteAddr()`; no code reads `X-Forwarded-For` (keep the grep clean).
- On a 429 the limiter logs the key at INFO (`log.info("auth rate limit hit for {}", key)`) — check that P20-01 did it;
  add it if missing.
- Tests (real Tomcat, `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers; MockMvc bypasses the valve):
  1. Default config, client on loopback (trusted), `X-Forwarded-For: 1.2.3.4, 5.6.7.8` → limiter key / remote address
     is `5.6.7.8`; distinct XFF values land in distinct buckets.
  2. Property `server.tomcat.remoteip.internal-proxies=^$` (regex matching nothing) → forged XFF does not change the
     bucket: the attempt after the allowance with a new XFF still gets 429.
  3. `X-Forwarded-Proto: https` from a trusted peer → `request.isSecure()` is true (OAuth redirect scheme).
- (`deployment.md` env row for `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`, the Railway/Render trust note, the
  self-hosted caveat and the commented `env.example` line are P20-08.)

## Acceptance criteria
- [ ] AC1: `application.yml` sets `server.forward-headers-strategy: native` with the one-line comment, and does not set
  `server.tomcat.remoteip.internal-proxies`.
- [ ] AC2: With the default config, a client on loopback sending `X-Forwarded-For: 1.2.3.4, 5.6.7.8` is limited as
  `5.6.7.8`: after `5.6.7.8`'s allowance is spent, a request with `X-Forwarded-For: 9.9.9.9` is not `429`, and another
  request as `5.6.7.8` is `429`.
- [ ] AC3: With `server.tomcat.remoteip.internal-proxies=^$`, changing the `X-Forwarded-For` value does not give a new
  bucket: after the allowance is spent, a request with a new `X-Forwarded-For` value still gets `429`.
- [ ] AC4: With the default config, `X-Forwarded-Proto: https` from the trusted loopback peer makes the request secure
  (observable effect, for example the scheme of the OAuth authorization redirect, or another effect the implementer
  names in Notes).
- [ ] AC5: `grep -rn "X-Forwarded-For" src/main/java` finds nothing.

## Test plan
- AC2, AC4: integration, `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestContainerBase`, a real HTTP client,
  low auth limit (for example `mindforge.rate-limits.auth.requests=2`, `window=PT30S`) —
  `src/test/java/dev/mindforge/integration/api/ClientAddressTest.java` (new). If AC4 needs an OAuth client
  registration, set test client id/secret properties in that class only.
- AC3: integration, a second nested or separate test class with `server.tomcat.remoteip.internal-proxies=^$` (its own
  Spring context), same folder.
- AC1, AC5: review and grep; full gate `mvn -B verify -DskipFrontend=true`.

## Files likely touched
- `src/main/resources/application.yml`
- `src/test/java/dev/mindforge/integration/api/ClientAddressTest.java` (new)
- `src/main/java/dev/mindforge/api/config/AuthRateLimitInterceptor.java` (only if the INFO 429 log is missing)

## Out of scope
- A new `TRUSTED_PROXIES` / `mindforge.*` setting or any code that parses `X-Forwarded-For`.
- Setting `server.tomcat.remoteip.internal-proxies` in `application.yml`.
- Docs (`deployment.md`, `env.example`) — P20-08. ADR 0039 exists.

## Notes
