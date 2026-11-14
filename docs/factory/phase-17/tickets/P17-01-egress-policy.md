---
id: P17-01
title: Add the EgressPolicy that admits only http/https URLs on default ports whose every address is public unicast
status: done
plan_tasks: [17.1, 17.3]
decisions: [D02, D13]
depends_on: []
size: M
issue: 22
commit: fda2bb4
fix_rounds: 1
---
## Goal
`dev.mindforge.infrastructure.security.EgressPolicy` exists. `check(String url)` returns the normalised `URI` of an
admitted URL, or throws `EgressDeniedException` with a fixed `Reason`. `resolve(String host)` returns the host's
addresses only when every address is public unicast. Tests reach a loopback mock server only through a package-private
constructor that production code cannot call.

## Context
- Phase 17 (Article Fetcher) fetches external articles that uploaded documents link to. This ticket is the SSRF
  control; the HTTP adapter (P17-05) calls it for every hop and connects only to the addresses `resolve` returns.
- `src/main/java/dev/mindforge/infrastructure/security/` holds `UploadSanitizer`, `JwtService`,
  `BCryptPasswordHasher`. `UploadSanitizer` is the shape to follow (fixed allowlist, final class).
- `docs/standards/security/web-security.md` § "Untrusted Input Handling" already shows `egressPolicy.check(externalUrl)`.
  `docs/standards/global/validation.md`: allowlists over blocklists.
- ADR 0022 (`docs/adr/0022-egress-allowlist-schemes-ports-public-addresses.md`, Accepted) is binding: schemes and
  ports are constants, address rule is a positive "is public unicast" predicate, test seam is a constructor seam, not a
  production property. ADR 0025 requires that the client connects to the checked address (no second resolution).
- The Spring bean is wired in P17-05 (`ArticleFetchConfig`); this ticket adds no bean.
- Unit tests in `src/test/java/dev/mindforge/unit/...` run in surefire; tests under
  `src/test/java/dev/mindforge/infrastructure/...` (no `unit` segment) also run in surefire (only `**/integration/**`
  and `**/e2e/**` are excluded) and can reach package-private members.

## Decisions to apply
**D02 — Decision:** `EgressPolicy` allows only schemes `http`/`https`, ports default/80/443, no userinfo, and a host
whose every resolved address is public unicast (a positive `isPublicUnicast` predicate that fails closed). Schemes and
ports are constants. Add no host list and no egress section in `AppProperties`; the default is "any public host",
gated only by `ProcessingSettings.fetchExternalArticles`.

**D02 — Implementation notes:**
- `dev.mindforge.infrastructure.security.EgressPolicy`, final class, bean built in a `@Configuration` (P17-05).
  `URI check(String url)` returns the normalised URI or throws `EgressDeniedException` with a `Reason` enum: SCHEME,
  PORT, USERINFO, HOST, ADDRESS, UNRESOLVABLE; fixed messages.
- Expose the resolved, vetted addresses (`List<InetAddress> resolve(String host)`, reject if ANY is not public) so
  the fetcher connects to them.
- Constants: `ALLOWED_SCHEMES = Set.of("http","https")` (case-insensitive); ports {-1 -> default, 80, 443}.
- `static boolean isPublicUnicast(InetAddress)`: false for anyLocal, loopback, linkLocal, siteLocal, multicast,
  0.0.0.0/8, 100.64.0.0/10, fc00::/7, fe80::/10; unwrap IPv4-mapped/compatible (and NAT64 64:ff9b::/96) and recheck.
- No new env var, no deployment.md entry for egress.
- `EgressPolicyTest` (`@ParameterizedTest`): `file:`, `data:`, `ftp:`, `jar:`, `http://127.0.0.1`, `http://[::1]`,
  10/8, 172.16/12, 192.168/16, 169.254.169.254, 100.64.0.1, `[fd00::1]`, `[fe80::1]`, `[::ffff:127.0.0.1]`,
  `http://2130706433/`, `0.0.0.0`, `[::]`, userinfo URL, `:8080`; a public address passes.
- From D04 (same class): `check` parses and checks IP-literal hosts itself (including `http://2130706433/` and
  `http://0x7f.1/`, which must be refused).

**D13 — Decision:** `EgressPolicy` gets one package-private constructor that takes an injected resolver, an address
predicate and a port predicate. Production code reaches only the strict public constructor. Tests build relaxed
policies through a test-tree factory in the same package.

**D13 — Implementation notes:**
- `public EgressPolicy()`: strict. Resolver `InetAddress::getAllByName`, `EgressPolicy::isPublicUnicast`, ports
  {-1, 80, 443}. The `@Configuration` bean calls only this constructor.
- `EgressPolicy(Function<String, InetAddress[]> resolver, Predicate<InetAddress> addressAllowed, IntPredicate portAllowed)`:
  package-private. The public constructor delegates to it. `check` (including IP-literal hosts) and `resolve` use only
  these three fields. No other path. (The resolver may wrap `UnknownHostException`; an unknown host gives UNRESOLVABLE.)
- No `AppProperties` field, no env var, no boolean flag. Never widen the constructor to `public`.
- Test tree `src/test/java/dev/mindforge/infrastructure/security/TestEgressPolicies.java` (public final, same package):
  - `static EgressPolicy admitting(String host, InetAddress address, int port)`: the resolver maps only `host` to
    `address`; any other non-literal host is UNRESOLVABLE, so tests never use real DNS. Predicate
    `a -> isPublicUnicast(a) || a.equals(address)`. Ports: default/80/443 or `port`.
  - `static EgressPolicy strictAddresses(String host, InetAddress address, int port)`: same resolver and port, but the
    predicate is `isPublicUnicast` only (for the policy-violation tests).
- `EgressPolicyTest` (production constructor): `http://example.com:8080/` -> PORT, proving the test port seam does not
  leak into the strict policy.

## Acceptance criteria
- [x] AC1: `EgressPolicy` is a final class in `dev.mindforge.infrastructure.security` with a public no-arg strict
  constructor and exactly one other constructor, package-private, taking `(Function<String, InetAddress[]>,
  Predicate<InetAddress>, IntPredicate)`.
- [x] AC2: `check` refuses `file:`, `data:`, `ftp:`, `jar:` and a URL with no scheme with reason SCHEME; scheme match is
  case-insensitive (`HTTPS://` is admitted when the rest is valid).
- [x] AC3: `check` refuses any explicit port other than 80/443 with PORT (`http://example.com:8080/`), and a URL with
  userinfo (`http://user:pw@example.com/`) with USERINFO; a URL with no host gives HOST.
- [x] AC4: `check` refuses with ADDRESS every literal in the D02 list: `127.0.0.1`, `[::1]`, 10.x, 172.16.x, 192.168.x,
  `169.254.169.254`, `100.64.0.1`, `0.0.0.0`, `[::]`, `[fd00::1]`, `[fe80::1]`, `[::ffff:127.0.0.1]`,
  `[::ffff:10.0.0.1]`, `[64:ff9b::7f00:1]`, `http://2130706433/`, `http://0x7f.1/`.
- [x] AC5: `check` admits a public host name (no resolution happens in `check`) and a public IP literal (e.g. `https://93.184.215.14/`) and returns a URI.
- [x] AC6: `resolve(host)` returns the addresses when all are public unicast, throws ADDRESS when any one is not
  (mixed public + private answer), and UNRESOLVABLE when the resolver fails.
- [x] AC7: `isPublicUnicast` is false for every range listed in D02 and true for a public IPv4 and a public IPv6
  (e.g. `2606:4700::1`).
- [x] AC8: `EgressDeniedException` messages are fixed text per reason; they contain no part of the URL.
- [x] AC9: `TestEgressPolicies.admitting` and `strictAddresses` exist in the test tree only; no `src/main` class calls
  the package-private constructor.

## Test plan
- Unit (plain JUnit 5 + AssertJ, `@ParameterizedTest`, no Spring): `src/test/java/dev/mindforge/unit/infrastructure/security/EgressPolicyTest.java`
  (new) for AC2–AC5, AC7, AC8 through the public constructor. `check` does not resolve a host name (only
  `resolve` does), so these tests need no DNS. AC6 (resolver cases) goes in the same-package test below, with an
  injected resolver; no test uses real DNS.
- `src/test/java/dev/mindforge/infrastructure/security/TestEgressPolicies.java` (new, helper, not a test) for AC9;
  a short same-package test `src/test/java/dev/mindforge/infrastructure/security/TestEgressPoliciesTest.java` (new)
  covers AC6 and shows `admitting` admits only the mapped host/address/port and `strictAddresses` refuses it with
  ADDRESS.

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/security/EgressPolicy.java` (new)
- `src/main/java/dev/mindforge/infrastructure/security/EgressDeniedException.java` (new)
- `src/test/java/dev/mindforge/unit/infrastructure/security/EgressPolicyTest.java` (new)
- `src/test/java/dev/mindforge/infrastructure/security/TestEgressPolicies.java` (new)
- `src/test/java/dev/mindforge/infrastructure/security/TestEgressPoliciesTest.java` (new)

## Out of scope
- The HTTP client, `CheckedDnsResolver`, redirects and the bean (P17-05).
- Any host allowlist, any `AppProperties` or env setting for egress, any "allow loopback" flag.
- Wrapping Spring AI, OTLP or OAuth2 clients (ADR 0023).

## Notes
- Decided: `isPublicUnicast` is `public static` (not package-private) because the unit-package `EgressPolicyTest` covers AC7 through it; it is a pure predicate with no seam.
- Decided: `EgressDeniedException extends SecurityException` with `reason()`, because the domain already uses `SecurityException` for refusals (`NotOwnerException`, `AccountException`).
- Decided: an unparseable URL (`URISyntaxException`) is refused with HOST, because the Reason enum is fixed and no other reason fits better.
- Decided: a host whose last label is a number is parsed as IPv4 by the WHATWG rule (decimal/octal/hex, 1-4 parts); a non-public result gives ADDRESS, a public one in a non-canonical form (e.g. `http://1572395790/`) gives HOST, because the client must never resolve a non-canonical numeric host itself. A registry-based authority (no server host, e.g. `my_host.example.com`) gives HOST.
- Decided: `isPublicUnicast` also unwraps 6to4 (2002::/16) and treats IPv4 224/3 (multicast, reserved, broadcast) and every IPv6 outside 2000::/3 as not public, because the predicate must fail closed.
- Decided: the production resolver wraps `UnknownHostException` in `UncheckedIOException`; `resolve` maps that, or an empty answer, to UNRESOLVABLE, and a blank host to HOST (`getAllByName("")` returns loopback).
- Decided: the `TestEgressPolicies` resolver resolves only the mapped host; every other host, literals included, is UNRESOLVABLE, because `check` already refuses non-public literals and no test needs a public literal to resolve.
- AC map (tester r1): AC1 -> EgressPolicyTest.shouldBeAFinalClassWithOnlyAStrictPublicAndOnePackagePrivateSeamConstructor; AC2 -> shouldRefuseANonHttpScheme, shouldMatchTheSchemeCaseInsensitively; AC3 -> shouldRefuseAPortOtherThanTheDefaults, shouldRefuseUserinfo, shouldRefuseAUrlWithoutAValidHost; AC4 -> shouldRefuseANonPublicAddressLiteral; AC5 -> shouldAdmitAPublicHostNameWithoutResolvingIt, shouldAdmitAPublicAddressLiteral; AC6 -> TestEgressPoliciesTest (shouldReturnTheAddresses..., shouldRefuseAHostWhenAnyAddress..., shouldRefuseAHostTheResolverCannotResolve); AC7 -> shouldNotTreatASpecialRangeAsPublicUnicast, shouldTreatAPublicAddressAsPublicUnicast; AC8 -> shouldUseAFixedMessagePerReason, shouldKeepTheUrlOutOfTheMessage; AC9 -> TestEgressPoliciesTest.shouldAdmitOnlyTheMappedHostAddressAndPort, shouldRefuseTheMappedLoopback...; grep shows no src/main call of the seam constructor.
