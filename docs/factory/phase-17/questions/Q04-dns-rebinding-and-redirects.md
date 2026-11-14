---
id: Q04
status: decided
decision: D04
impact: high
blocks: [W01, W02]
---
## Question
How does the fetch keep the address check true at connect time — DNS resolution, DNS rebinding and HTTP redirects —
and which HTTP client does it use?

## Context
- A URL check on the hostname string alone is bypassed by a public name that resolves to `127.0.0.1` or
  `169.254.169.254`, by a second DNS answer between check and connect (rebinding), by a redirect to a private
  address, and by odd IP spellings (`http://2130706433/`, `0x7f.1`, `[::ffff:127.0.0.1]`).
- No HTTP client exists in `src/main`. Options in reach without new dependencies: JDK `java.net.http.HttpClient`
  (redirect policy `NEVER`/`NORMAL`, no hook to pin an address) or Spring `RestClient` over it; Apache HttpClient 5 is
  not on the classpath.
- Plan 17.2 "fetches with a timeout"; nothing on redirects.

## Known options
- A: Resolve the host with `InetAddress.getAllByName`, reject if any address is not public, then connect to the checked
  address (send `Host` header / SNI for the name). Redirects disabled in the client and followed by code, each hop
  re-checked, max 3–5 hops. Strong; needs care for TLS SNI with an IP connect.
- B: Resolve and check all addresses, then let the JDK client connect by name (small rebinding window accepted).
  Redirects followed manually with a re-check per hop.
- C: B with redirects not followed at all (a 3xx is a failed fetch). Simplest; many article URLs redirect
  (http→https, short links) and would fail.

## Constraints
- Rejects private ranges, `file://`, `data:` (plan 17.1); allowlist form per Q02.
- Every hop of a fetch passes `EgressPolicy` (checklist "All outbound HTTP goes through `EgressPolicy`").
- Minimal dependencies (`docs/standards/global/conventions.md`).
- Blocking I/O is fine on virtual threads; no reactive types (CLAUDE.md API rules).
