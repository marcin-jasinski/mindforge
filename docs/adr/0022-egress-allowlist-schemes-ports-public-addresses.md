# The egress allowlist is schemes, ports and public unicast addresses, with no host list

Status: Accepted (2026-11-14).

`EgressPolicy` (`dev.mindforge.infrastructure.security`) allows a URL only when all of these are true: the scheme is
`http` or `https`; the port is the scheme default, 80 or 443; the URL has no userinfo; and every address the host
resolves to is public unicast. The address rule is a positive predicate (`isPublicUnicast`), not a list of bad
ranges. It rejects loopback, any-local, private, link-local (incl. 169.254.169.254), CGNAT, multicast, IPv6 ULA and
link-local, and unwraps IPv4-mapped and compatible forms to check the embedded IPv4. An unknown form fails closed.
The schemes and ports are constants, not properties. There is no host list, and `AppProperties` gets no egress
section. The only switch for article fetching stays `ProcessingSettings.fetchExternalArticles` (T28).

## Considered Options

- **A host allowlist that must be non-empty**: the default empty list makes `fetchExternalArticles=true` fetch
  nothing, without an error — a second off switch against plan 17.2 and T28.
- **An optional operator host allowlist (suffix match, empty = all public hosts)**: a property, an env var and
  suffix-match edge cases (dot boundary, trailing dot, case, IDN) that no requirement asks for. A host list does not
  stop SSRF: an allowed host can resolve to, or redirect to, an internal address.
- **A host allowlist only, or a host denylist**: does not reject private ranges, or is a blocklist.

## Consequences

- Any public host is reachable when fetching is enabled. The damage an article can do is bounded by the create-only
  `ARTICLE` run (no Supersede), not by the host.
- The SSRF control is the address check. The fetcher must connect to the checked address (no second resolution) and
  check every redirect hop again (Q04).
- An operator host list can be added later as a pure narrowing filter without a change to this contract; it needs a
  new decision.
- The loopback mock server in tests needs a constructor seam (an injected address predicate or resolver), not a
  production property.

Decided in [D02](../factory/phase-17/decisions/D02-what-the-egress-allowlist-is.md) by the dark-factory run
`phase-17`.
