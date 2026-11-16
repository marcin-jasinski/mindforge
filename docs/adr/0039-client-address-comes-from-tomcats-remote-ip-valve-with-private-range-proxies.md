# Client address comes from Tomcat's RemoteIpValve with private-range proxies

Status: Accepted (2026-11-16).

The server sets `server.forward-headers-strategy: native` and keeps Spring Boot's default
`server.tomcat.remoteip.internal-proxies` (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `100.64.0.0/10`,
`127.0.0.0/8`, `169.254.0.0/16`, `fc00::/7`, `fe80::/10`, `::1/128`). Tomcat's `RemoteIpValve` reads `X-Forwarded-For`
and `X-Forwarded-Proto` only when the direct peer is in that range, and walks `X-Forwarded-For` from the right, stopping
at the first address outside the range. So `request.getRemoteAddr()` is the real client behind the Railway or Render
proxy, and a client cannot choose its own address: a public peer's header is ignored, and a forged left-hand entry is
never reached. `request.isSecure()` and the scheme follow `X-Forwarded-Proto`, so OAuth redirect URIs use https behind
the proxy. The auth rate limiter (ADR 0038) keys on `getRemoteAddr()`; no application code reads `X-Forwarded-For`. An
operator can narrow or widen the range with the existing Spring variable `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`.

## Considered Options

- **Trusted ranges from a new `TRUSTED_PROXIES`, default "trust none"**: fail closed, but an unset value on Railway or
  Render puts every user in the proxy's bucket, so ten failed logins anywhere lock out all sign-ins. The operator also
  cannot fill it exactly, because neither platform publishes its proxy addresses. It is a new name for a property Spring
  already binds.
- **No forwarded headers; document the limit as global behind a proxy**: the same global lockout, by design.
- **The limiter parses the rightmost `X-Forwarded-For` entry itself, given a hop count**: hand parsing of untrusted
  input beside the framework (`web-security.md`, `validation.md`), a second notion of the client address, the scheme
  stays wrong, and a wrong hop count trusts a client-chosen entry.

## Consequences

- That Railway and Render reach the app from a private address is observed, not documented. If a proxy comes from
  outside the range, the header is ignored and the limit becomes global: an availability fault, not a bypass. The
  limiter logs its key on a 429 so this shows up.
- A self-hosted setup whose peer is a private address that is not a proxy (for example the Docker userland proxy) trusts
  a client's `X-Forwarded-For`. `deployment.md` states this and shows the override.
- In Boot 4.1.0 / Tomcat 11.0.22 an empty `internal-proxies` value trusts no peer (the valve's matchers are null). The
  Spring Boot how-to says an empty value trusts all proxies; do not rely on either reading, and use a value that matches
  nothing (for example `^$`) to switch trust off.
- Tests that check the client address use a real server (`RANDOM_PORT`); MockMvc does not run the valve.
- Moving to an explicit, required range is one property and a new decision.

Decided in [D02](../factory/phase-20/decisions/D02-client-address-behind-proxy.md) by the dark-factory run
`phase-20`.
