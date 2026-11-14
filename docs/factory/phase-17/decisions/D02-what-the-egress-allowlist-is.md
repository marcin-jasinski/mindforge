---
id: D02
question: Q02
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q02.r1.tally.md]
adr: docs/adr/0022-egress-allowlist-schemes-ports-public-addresses.md
---
## Decision
Use A: `EgressPolicy` allows only schemes `http`/`https`, ports default/80/443, no userinfo, and a host whose every
resolved address is public unicast (a positive `isPublicUnicast` predicate that fails closed). Schemes and ports are
constants. Add no host list and no egress section in `AppProperties`; the default is "any public host", gated only by
`ProcessingSettings.fetchExternalArticles`.

## Why
- Plan 17.1 (implementation-plan.md:1522-1523) asks for an allowlist that rejects private ranges, `file://`, `data:`;
  the scheme allowlist removes `file:`/`data:`, the address predicate removes private ranges.
- validation.md:15-16 "Allowlists Over Blocklists" is met: schemes and ports are literal allowlists, and the address
  rule is written as "is public unicast", not a list of bad ranges.
- Plan 17.2 (implementation-plan.md:1533) and T28 #1 (28-minor-review-findings.md:42-43) name one switch; C adds a
  second, silent one.
- SSRF is an address problem: a host list does not stop an allowed host that resolves or redirects inward.
- Same shape as `UploadSanitizer` (fixed allowlist via constructor). minimal-implementation.md:3,15.

## Rejected
- B: an operator host list with suffix-match edge cases that no requirement asks for; additive later if needed.
- C: default empty list makes "enabled" fetch nothing — a second off switch (verified hard objection).

## Dissent
None on the choice. Risk to watch: the address check is the whole SSRF control, so DNS rebinding and redirects (Q04)
must use the checked address and re-check each hop; if Q03 routes model/OTLP endpoints through `EgressPolicy`, a host
exception list may be needed — that is a new decision.

## Implementation notes
- `dev.mindforge.infrastructure.security.EgressPolicy`, final class, bean built in a `@Configuration`.
  `URI check(String url)` returns the normalised URI or throws a meaningful exception (e.g.
  `EgressDeniedException` with a `Reason` enum: SCHEME, PORT, USERINFO, HOST, ADDRESS, UNRESOLVABLE); fixed messages.
- Expose the resolved, vetted addresses (e.g. `List<InetAddress> resolve(URI)`, reject if ANY is not public) so the
  fetcher connects to them; redirect/rebinding handling per Q04.
- Constants: `ALLOWED_SCHEMES = Set.of("http","https")` (case-insensitive); ports {-1 -> default, 80, 443}.
- `static boolean isPublicUnicast(InetAddress)`: false for anyLocal, loopback, linkLocal, siteLocal, multicast,
  0.0.0.0/8, 100.64.0.0/10, fc00::/7, fe80::/10; unwrap IPv4-mapped/compatible (and NAT64 64:ff9b::/96) and recheck.
- Test seam: package-private constructor taking a `Predicate<InetAddress>` or resolver for the loopback mock server
  (Q13); production wiring uses the strict one. No new env var, no deployment.md entry for egress.
- `EgressPolicyTest` (`@ParameterizedTest`): `file:`, `data:`, `ftp:`, `jar:`, `http://127.0.0.1`, `http://[::1]`,
  10/8, 172.16/12, 192.168/16, 169.254.169.254, 100.64.0.1, `[fd00::1]`, `[fe80::1]`, `[::ffff:127.0.0.1]`,
  `http://2130706433/`, `0.0.0.0`, `[::]`, userinfo URL, `:8080`; a public address passes.
