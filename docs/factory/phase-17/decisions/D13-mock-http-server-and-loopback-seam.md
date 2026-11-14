---
id: D13
question: Q13
status: decided
method: unanimous
class: local
votes: { pragmatist: S, architect: S, skeptic: S }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q13.r1.tally.md]
adr: 
---
## Decision
Use S: the mock-HTTP-server test uses the JDK `com.sun.net.httpserver.HttpServer` (no new dependency). `EgressPolicy`
gets one package-private constructor that takes an injected resolver, an address predicate and a port predicate.
Production code reaches only the strict public constructor (`InetAddress::getAllByName`, `isPublicUnicast`, ports
default/80/443). Tests build relaxed policies through a test-tree factory in the same package. That factory maps a
`.test` host name to the server's loopback address, and admits only that address and the server's ephemeral port. The
request still goes through the real `CheckedDnsResolver` pin. The policy-violation test uses the same mapping with
the strict address predicate and expects a refusal.

## Why
- ADR 0022 (Accepted) asks for "a constructor seam (an injected address predicate or resolver), not a production
  property" (`docs/adr/0022-...:30-31`). D02:47-48 and D04:50-51 already name this seam.
- D04:52-54 / ADR 0025: `CheckedDnsResolver` connects to exactly what `EgressPolicy.resolve` returned. Thus "check a
  public address, connect to loopback" cannot be built. The test resolver must return 127.0.0.1, and a predicate must
  admit it. All three ballots reach this constructor.
- ADR 0022:6,10: ports are constants. An ephemeral-port server fails at `check` with PORT before any address check.
  Thus the seam must also admit the test port, or the "address" refusal tests prove nothing.
- C adds a test dependency that has no benefit. JDK `HttpServer` serves every case in D09:59.

## Rejected
- C (WireMock): new dependency, against the "minimal dependencies" constraint.
- Literal A (split check/connect addresses): conflicts with the D04/ADR 0025 address pin.
- Literal B (boolean "allow loopback"): admits all loopback. Admitting the one server address is narrower.

## Dissent
None on the choice. Skeptic's risk to watch: the relaxed constructor must stay unreachable from main wiring. Package
privacy enforces this. Do not widen the constructor to `public` to reach it from `infrastructure.web` or
`infrastructure.config`.

## Implementation notes
- `dev.mindforge.infrastructure.security.EgressPolicy`:
  - `public EgressPolicy()`: strict. Resolver `InetAddress::getAllByName`, `EgressPolicy::isPublicUnicast`, ports
    {-1, 80, 443}. The `@Configuration` bean calls only this constructor.
  - `EgressPolicy(Function<String, InetAddress[]> resolver, Predicate<InetAddress> addressAllowed, IntPredicate portAllowed)`:
    package-private. The public constructor delegates to it. `check` (including IP-literal hosts) and `resolve` use
    only these three fields. No other path.
  - No `AppProperties` field, no env var, no boolean flag.
- Test tree `src/test/java/dev/mindforge/infrastructure/security/TestEgressPolicies.java` (public final, same package):
  - `static EgressPolicy admitting(String host, InetAddress address, int port)`: the resolver maps only `host` to
    `address`. Any other non-literal host throws `UnknownHostException` (UNRESOLVABLE), so the test never uses real DNS.
    Predicate `a -> isPublicUnicast(a) || a.equals(address)`. Ports: default/80/443 or `port`.
  - `static EgressPolicy strictAddresses(String host, InetAddress address, int port)`: the same resolver and port, but
    the predicate is `isPublicUnicast` only (used by the policy-violation tests).
- `dev.mindforge.infrastructure.web.HttpArticleSourceTest` (the mock-server test per D09:57-59, not
  `ArticleFetcherTest`): `HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)`, stop in
  `@AfterEach`. URLs use the `articles.test` name (RFC 2606 reserved) and the server port. Build the client through
  the same factory method as the production `@Configuration`, with short test timeouts. No Spring context. Use AssertJ.
- Cases that must be covered:
  - Admitted fetch succeeds and returns the converted article.
  - Policy violation: `strictAddresses` policy, `articles.test` -> 127.0.0.1, reason POLICY. Assert that the server
    received zero requests.
  - Redirect to `http://169.254.169.254/` and to `http://127.0.0.2:<port>/` (loopback, but not the admitted address)
    are refused, and the server got only the first request. Do not use the admitted 127.0.0.1 as the "loopback
    redirect" target; the seam admits it.
  - The 6th redirect hop fails.
  - Timeout (the handler sleeps past the response timeout).
  - Non-HTML content type.
  - Over-size body.
- `EgressPolicyTest` (production constructor): add `http://example.com:8080/` -> PORT. This proves that the test port
  seam does not leak into the strict policy.
