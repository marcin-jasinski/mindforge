---
id: D07
question: Q07
status: decided
method: majority
class: adr
votes: { pragmatist: A, architect: A, skeptic: C }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q07.r1.tally.md]
adr: docs/adr/0029-discord-attachments-downloaded-by-the-platform-client-from-a-fixed-cdn-host-list.md
---
## Decision
Use A: treat Discord's API and its attachment CDN as the operator-configured platform endpoint (ADR 0023 trust tier),
and download `/upload` attachment bytes with JDA's own client. Before any byte is read, require the attachment URL to
be `https`, default port, no userinfo, with a host in the fixed set `cdn.discordapp.com`, `media.discordapp.net`, and
require the declared `Attachment.getSize()` to be within `mindforge.upload.max-size`. Read at most max-size + 1 bytes;
more than max-size fails, never truncates. Record this as ADR 0029, which narrows ADR 0023's "bot links" to URLs a
user chooses. Extend `OutboundHttpGuardTest` with JDA/OkHttp download markers and allow only the one downloader file.

## Why
- The URL is issued by Discord in the interaction payload over the bot's authenticated, operator-configured session; the user does not choose it. ADR 0023:7-8 trusts operator endpoints.
- ADR 0023:27-28 asks for "a new decision" for bot links; ADR 0029 is that decision, so no accepted ADR is contradicted.
- The fixed CDN host list is the property that matters (the file is from Discord); B alone admits any public host.
- B/C need a second HttpClient 5 fetcher or a refactor of `HttpArticleSource` (219 lines, HTML-shaped, pinned by path in the guard test) to guard Discord's own CDN: more surface, no closed threat (minimal-implementation.md).
- The same rule carries to Phase 19 Slack files, which need the bot token on the request and must not go through a generic fetcher.
- Size: declared size checked first, then a capped read (Phase 17 `readCapped` precedent, HttpArticleSource.java:199), then `UploadPolicy.admit` on the real byte length (IngestionService.java:88).

## Rejected
- B: correct, but a new fetch port and client for a platform-owned CDN, and no assertion that the file comes from Discord.
- C: defense in depth at the cost of B plus A; the extra layer guards against Discord itself redirecting into the private network.
- D variants: string-only check (no pinned DNS), no ADR, or an OkHttp `Dns` control (rejected by ADR 0025:22-23).

## Dissent
Skeptic (C, 0.65): JDA's OkHttp follows redirects by default, so the host check covers only the first hop, and the new
guard markers are easier to bypass than a directory rule. Risk to watch: if Discord's CDN is ever seen to redirect, or
a non-attachment URL can reach the downloader, switch the one download method to an egress-checked client (C).

## Implementation notes
- One class in the Discord adapter package (package per Q09), e.g. `DiscordAttachments` (final): `byte[] read(Message.Attachment attachment, long maxBytes)`.
  - `private static final Set<String> CDN_HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");`
  - Parse `attachment.getUrl()` with `URI`; require scheme `https`, `getUserInfo() == null`, port `-1` or 443, lower-cased host in `CDN_HOSTS`; else throw a typed exception (no bare `RuntimeException`) mapped to a fixed reply. Log at WARN the host only, never the signed URL query.
  - `if (attachment.getSize() > maxBytes)` -> the same "file too large" outcome as web upload; no download call.
  - `attachment.getProxy().download()` -> `get(timeout, SECONDS)` with a fixed deadline constant (e.g. 60 s) unless Q15 adds a setting; in try-with-resources `in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1))`; length > maxBytes -> too large. Never truncate.
- Handler order in `/upload`: allowlist (ADR 0028) -> identity -> ownership -> `deferReply` -> `DiscordAttachments.read` (blocking on a virtual thread, outside any transaction) -> `IngestionService.ingest(new DocumentUpload(..., UploadSource.DISCORD, filename, contentType, bytes, ...))`; `UploadPolicy.admit` runs there as on web and CLI.
- `OutboundHttpGuardTest`: add markers `"okhttp3"`, `".getProxy()"`, `".download("`; add the downloader's exact path to `OPERATOR_ENDPOINT_FILES` with a comment citing ADR 0029; add a case that another Discord file containing `.download(` is reported.
- Docs: one sentence in `web-security.md` § Untrusted Input Handling naming ADR 0029; a cross-reference line is enough, ADR 0023 stays Accepted.
- Unit tests (no Spring, fake `Attachment`/`AttachmentProxy`): off-list host refused; `http` refused; userinfo refused; declared size over cap refused and `download()` never called; body of maxBytes + 1 fails; body of exactly maxBytes passes.
- Dependency check: JDA brings OkHttp; ADR 0025 notes `okhttp-4.12.0` and `okhttp-jvm-5.3.2` already both ship `okhttp3`. Verify JDA runs against the resolved OkHttp in the integration gate.
