---
id: Q07
status: decided
decision: D07
impact: high
blocks: [W08]
---
## Question
How does `/upload` get the attachment's bytes — through JDA's own HTTP client to Discord's CDN as a trusted platform
endpoint, or through `EgressPolicy` — and how is the size limit applied before download?

## Context
A slash-command attachment arrives as metadata (`Attachment`: filename, content type, size, `url`/`proxyUrl` on
`cdn.discordapp.com`/`media.discordapp.net`). The bytes need an HTTP GET. ADR 0023 says `EgressPolicy` guards URLs from
content or users, operator endpoints are trusted, and § Consequences: "If a feature lets a user … set an endpoint URL
(… bot links), that URL is untrusted and must go through `EgressPolicy`. That change needs a new decision."
`OutboundHttpGuardTest` (`src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java`)
allows HTTP-client markers only in `infrastructure/web/` and `api/config/SecurityConfig.java`; JDA's OkHttp
(`attachment.getProxy().download()`) contains none of its markers, so the guard would not see it. The URL is set by
Discord, not typed by the user, but it arrives inside user-triggered event data. `mindforge.upload.max-size` (50 MB)
applies "on every upload channel"; Discord's own attachment limit is smaller for most guilds.

## Known options
- A: Treat Discord's API and CDN as an operator-configured platform endpoint (the bot token is operator config): download
  with JDA's client, but only after checking that the attachment URL is `https` on a fixed Discord CDN host list and
  that the declared size is within `max-size`; cap bytes read. Record as a new ADR amending the ADR 0023 scope; add a
  JDA-download marker to the guard test with the `discord` package allowed by exact path.
- B: Treat the URL as untrusted: download through `EgressPolicy` with the pinned HttpClient 5 client
  (reuse/extend the `infrastructure.web` adapter behind a port), public-address check, size cap, no redirects. Uniform
  with ADR 0022/0025, more code; Discord CDN addresses are public, so it passes.
- C: A + B: host check, then fetch through the egress-checked client.

## Constraints
- ADR 0022, 0023, 0025 are accepted; contradicting them is a blocker — so the answer must either fit ADR 0023's scope or
  be recorded as the "new decision" it asks for.
- `UploadSanitizer`/`UploadPolicy` still admits the filename, MIME type and size (same as web and CLI).
- Never read an unbounded body; a too-large file fails, it is never truncated (Phase 17 precedent).
- `OutboundHttpGuardTest` must stay green and should still catch new unchecked HTTP code.
