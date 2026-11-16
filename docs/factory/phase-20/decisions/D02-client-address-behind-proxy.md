---
id: D02
question: Q02
status: decided
method: majority
class: adr
votes: { pragmatist: A, architect: B, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q02.r1.tally.md]
adr: docs/adr/0039-client-address-comes-from-tomcats-remote-ip-valve-with-private-range-proxies.md
---
## Decision
Use A: set `server.forward-headers-strategy: native` in `application.yml` and keep Spring Boot's default
`server.tomcat.remoteip.internal-proxies` (private, CGNAT, loopback, link-local and IPv6 ULA ranges). Tomcat's
`RemoteIpValve` then sets `request.getRemoteAddr()`, the scheme and `isSecure()` from `X-Forwarded-For` /
`X-Forwarded-Proto` only when the direct peer is in that range. The auth limiter (D01) keys on `getRemoteAddr()` and no
application code reads `X-Forwarded-For`. Document the existing Spring variable `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`
as the optional operator override. Add no new `TRUSTED_PROXIES` setting.

## Why
- The valve checks the peer against an allowlist and walks XFF right to left, so a forged left-hand value is never used
  (`RemoteIpValve.invoke`, tomcat-embed-core 11.0.22, verified). A public client cannot connect from a private address.
- Default range verified in `spring-boot-tomcat-4.1.0.jar`: it covers what Railway/Render use internally.
- A fails toward the global bucket, not toward a bypass: a proxy outside the range means the header is ignored.
- B's default ("trust none") gives the global-lockout defect of C on the primary deployment until the operator sets a
  value that Railway/Render do not publish (question Context).
- One YAML line; the override already exists through relaxed binding; the change is reversible in one line.
- The claim in two ballots that an empty internal-proxies value trusts all proxies is false for this version: Tomcat
  11.0.22 `setInternalProxies("")` nulls both matchers and trusts no peer. It did not decide the vote.

## Rejected
- B: fail-closed default reproduces the global-bucket lockout on Railway/Render; a new alias for a property Spring already binds.
- C: verified hard objection: every user shares the proxy's bucket, ten failed logins lock out all sign-ins.
- D: verified hard objection: hand-parsed untrusted XFF beside the framework, scheme stays wrong, wrong hop count trusts client data.

## Dissent
Architect (B): A trusts any private-range peer by an undocumented PaaS property, including a docker bridge on a
self-hosted setup with a userland proxy, and a maintainer cannot see from config that XFF is trusted. Watch: log the
limiter key on a 429 so a global bucket shows up, and document the self-hosted caveat and the override.

## Implementation notes
- `src/main/resources/application.yml`, under `server:` (line 75): `forward-headers-strategy: native` with a one-line
  comment: "X-Forwarded-* trusted only from server.tomcat.remoteip.internal-proxies (Spring default: private ranges)".
  Do not set `server.tomcat.remoteip.internal-proxies` in `application.yml`.
- Limiter (D01/W02): key is `request.getRemoteAddr()`; no code reads `X-Forwarded-For` (keep the grep clean).
- On a 429 the limiter logs the key at INFO (`log.info("auth rate limit hit for {}", key)`).
- `docs/project/deployment.md`: env table row for optional `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (comma-separated
  CIDR list or a Java regex; default Spring's private ranges); § Railway/Render note on the trust model; self-hosted
  caveat (a reverse proxy or docker userland proxy on a private address is trusted; set the override to its address).
  `env.example`: the variable, commented out.
- Tests (real Tomcat, `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers; MockMvc bypasses the valve):
  1. Default config, client on loopback (trusted), `X-Forwarded-For: 1.2.3.4, 5.6.7.8` → limiter key / remote address
     is `5.6.7.8`; distinct XFF values land in distinct buckets.
  2. Property `server.tomcat.remoteip.internal-proxies=^$` (regex matching nothing) → forged XFF does not change the
     bucket: the 11th attempt with a new XFF still gets 429.
  3. `X-Forwarded-Proto: https` from a trusted peer → `request.isSecure()` is true (OAuth redirect scheme).
- ADR 0039 (written): `docs/adr/0039-client-address-comes-from-tomcats-remote-ip-valve-with-private-range-proxies.md`.
