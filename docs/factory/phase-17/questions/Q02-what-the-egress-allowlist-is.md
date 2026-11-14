---
id: Q02
status: decided
decision: D02
impact: high
blocks: [W01, W05, W07]
---
## Question
What does `EgressPolicy` allow-list — schemes and ports only, an operator-configured host list, or both — and what is
the default?

## Context
- Plan 17.1: "Allowlist-based URL validator; rejects private IP ranges, `file://`, `data:`." It does not name what is
  on the allowlist.
- `docs/standards/global/validation.md`: "Allowlists Over Blocklists". `web-security.md` shows only
  `egressPolicy.check(externalUrl)`.
- Articles in learning material come from arbitrary hosts. A host allowlist that is empty by default makes the feature
  a second off switch; a scheme/port allowlist plus non-public-address rejection lets any public site through.
- `AppProperties` has no egress section; a new property block and env vars would be documented in
  `docs/project/deployment.md`.

## Known options
- A: Allowlist of schemes (`http`, `https`) and ports (80, 443); reject every address that is not public unicast
  (loopback, private, link-local incl. 169.254.169.254, CGNAT, multicast, unspecified, IPv6 ULA/link-local, IPv4-mapped
  forms). No host list.
- B: A plus an operator-configured host allowlist (`mindforge.articles.allowed-hosts`, suffix match); empty list means
  every public host. Lets an operator narrow egress without code.
- C: A plus a host allowlist that must be non-empty for anything to be fetched (default empty → nothing fetched even
  when the setting is on).

## Constraints
- Must reject `file://`, `data:` and private ranges (plan 17.1).
- Allowlists over blocklists (`docs/standards/global/validation.md`).
- Configuration through `AppProperties` + `@Configuration`; new env vars documented in `docs/project/deployment.md`.
