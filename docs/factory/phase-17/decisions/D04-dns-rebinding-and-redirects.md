---
id: D04
question: Q04
status: decided
method: unanimous
class: adr
votes: { pragmatist: D-HC5, architect: D-HC5, skeptic: D-HC5 }
vote_changes: { pragmatist: B->D-HC5, skeptic: D-OKHTTP->D-HC5 }
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q04.r1.tally.md, docs/factory/phase-17/ballots/Q04.r2.tally.md]
adr: docs/adr/0025-article-fetch-pins-checked-addresses-with-httpclient5.md
---
## Decision
Use D-HC5: the article fetcher uses Apache HttpClient 5 (`httpclient5`, version from the Spring Boot BOM) with a
custom `DnsResolver` that resolves the host through `EgressPolicy` and returns only checked public-unicast addresses,
so the client connects to exactly what was checked; TLS SNI and hostname verification stay on the name. Turn off the
client's redirect handling and follow 301/302/303/307/308 in code, resolve `Location` against the current URI, run
`EgressPolicy.check` on every hop, and stop after 5 hops with a fixed failure reason. Check IP-literal hosts in
`EgressPolicy.check`. Keep every other Spring HTTP client on the JDK transport with a property pin.

## Why
- ADR 0022 (Accepted) requires "connect to the checked address (no second resolution) and check every redirect hop
  again" (`docs/adr/0022-...:26-27`); D02:43 and D03:50 say the same. B resolves twice, so it is out.
- A resolver hook is the only way to pin the address with TLS by name; JDK `HttpClient` has no hook (A and E out in round 1).
- D-OKHTTP would rest the SSRF control on two `okhttp3` jars in the shipped artifact (`okhttp-4.12.0`, `okhttp-jvm-5.3.2`,
  confirmed with `unzip -l`), where jar order picks the class. D-HC5 is one BOM-managed artifact (5.6.1, BOM line 77),
  already in `~/.m2` so the offline `fast` gate works.
- With a resolver hook, the Q13 test seam (injected resolver) is the path the client connects through: one path, one test.
- All three lenses chose D-HC5 in round 2.

## Rejected
- A: connect to an IP literal on the JDK client needs JVM-wide restricted-header and hostname-verification flags.
- B: second resolution by the JDK client; violates ADR 0022; guarantee depends on `networkaddress.cache.ttl` and cannot be tested.
- C: a 3xx as failure breaks http->https and short-link articles.
- D-OKHTTP: SSRF control would depend on duplicate `okhttp3` classes from two libraries; version not BOM-managed.
- E: JVM-wide resolver filter also hits Postgres, OTLP and the dev model provider on localhost.

## Dissent
None in round 2. Round-1 pragmatist view: B is simpler and needs no new dependency. Risk to watch: with HC5 on the
classpath Spring's request-factory detection prefers it for every `RestClient`; the pin below must hold, and Spring AI
and OAuth2 calls must keep working (integration tests in the gate).

## Implementation notes
- `pom.xml`: `org.apache.httpcomponents.client5:httpclient5`, no version (Boot BOM), comment
  "SSRF: DnsResolver pins checked addresses (ADR 0025, Q04)". Add a line to `docs/project/tech-stack.md` "Key Dependencies Summary".
- `application.yml`: `spring.http.clients.imperative.factory: jdk` (the Boot 4 key; `spring.http.client.factory` is
  deprecated since 4.0.0). Do not expose the fetcher's client as a `ClientHttpRequestFactory` bean.
- `dev.mindforge.infrastructure.security.EgressPolicy` (per D02/ADR 0022): `URI check(String url)` (scheme http/https,
  default port, no userinfo, IP-literal hosts parsed and checked) and `List<InetAddress> resolve(String host)` (throw
  `EgressDeniedException` if ANY address fails `isPublicUnicast`). Resolver injected via constructor (Q13 seam;
  production `InetAddress::getAllByName`).
- `dev.mindforge.infrastructure.article.CheckedDnsResolver implements org.apache.hc.client5.http.DnsResolver`:
  `resolve(String host)` -> `egressPolicy.resolve(host).toArray(InetAddress[]::new)`; override `resolve(String, int)`
  too so no path skips the check; `resolveCanonicalHostname(host)` returns `host` unchanged.
- `@Configuration` builds one `CloseableHttpClient`: `PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(checkedDnsResolver)`,
  connect and response timeouts, `disableRedirectHandling()`, never `useSystemProperties()` (no system proxy).
- Fetcher adapter (behind the Q09 port): loop max 5 hops; `next = current.resolve(location)`; `egressPolicy.check(next)`
  before each request; body size cap (Q06); fixed failure texts ("too many redirects", "address not allowed").
- Guard test (D03): `org.apache.hc.client5` counts as an HTTP-client marker allowed only in the article fetch adapter.
- Tests: name mapped to 127.0.0.1 by the injected resolver is refused through the real client; redirect to
  `127.0.0.1` / `169.254.169.254` refused; 6th hop fails; `http://2130706433/`, `0x7f.1`, `[::ffff:127.0.0.1]` refused;
  http->https redirect to a public (test-admitted) host succeeds.
