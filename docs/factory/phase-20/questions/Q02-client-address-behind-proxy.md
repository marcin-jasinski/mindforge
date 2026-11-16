---
id: Q02
status: decided
decision: D02
impact: high
blocks: [W01, W02]
---
## Question
How does the server find the client address for the auth rate limit when it runs behind the Railway or Render proxy,
without letting a client choose its own address with a forged `X-Forwarded-For`?

## Context
- `deployment.md` § Railway: the app runs as a Docker service on Railway (or Render), both of which put a reverse
  proxy in front and set `X-Forwarded-For` / `X-Forwarded-Proto`.
- Today nothing reads forwarded headers: `application.yml` has no `server.forward-headers-strategy`, and no code calls
  `getRemoteAddr()` or reads `X-Forwarded-For`. So `request.getRemoteAddr()` in production is the proxy's address: a
  per-address limiter would put every user in one bucket, and ten failed logins anywhere would lock out all sign-ins.
- If the app trusts `X-Forwarded-For` from anyone, a client sets a new value per request and the limit never fires.
  Tomcat's `RemoteIpValve` (`server.forward-headers-strategy=native` + `server.tomcat.remoteip.internal-proxies`) only
  honours the header when the direct peer is a trusted proxy; its default trusted set is private (RFC 1918, loopback)
  ranges, which is what Railway/Render use internally, but that is not documented as a guarantee.
- Local `docker compose` and tests connect directly (no proxy).
- Turning on forwarded headers also changes `request.isSecure()`/scheme for OAuth redirect URIs (likely a fix, but a
  behaviour change).

## Known options
- A: `server.forward-headers-strategy=native` with Tomcat's default internal-proxy ranges; the limiter uses
  `getRemoteAddr()`.
- B: As A, but the trusted proxy ranges come from a required/optional setting (e.g. `TRUSTED_PROXIES`), default empty =
  trust no forwarded header (fail closed: limit by peer address).
- C: No forwarded headers; key on `getRemoteAddr()` and document that behind a proxy the limit is global (weak).
- D: The limiter reads the rightmost `X-Forwarded-For` entry itself when a setting says the app is behind N proxies.

## Constraints
- Never trust client-supplied data without a check (`validation.md`: allowlists over blocklists; server-side always).
- Operator configuration goes through env vars documented in `deployment.md` and `env.example` (`conventions.md`).
- One live instance; in-process limiter (ADR 0007, `deployment.md` § One live instance).
- A load-bearing choice gets an ADR in the newest ADR format.
