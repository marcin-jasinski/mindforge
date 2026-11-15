# Discord attachments are downloaded by the platform client from a fixed CDN host list

Status: Accepted (2026-11-14).

The `/upload` slash command gets an attachment's bytes through JDA's own client, not through `EgressPolicy`. Discord's
API and its attachment CDN are the platform the operator configures with the bot token, so they are trusted under
ADR 0023. The attachment URL is issued by Discord in the interaction payload; the user does not choose it. The trust
is checked, not assumed: before any byte is read, the URL must be `https` on the default port with no userinfo, and
its host must be `cdn.discordapp.com` or `media.discordapp.net`; any other URL fails closed. The declared size must be
within `mindforge.upload.max-size`, and the body is read to at most that size plus one byte: a larger body fails and is
never truncated. `UploadPolicy` then admits the filename, MIME type and real size as on web and CLI. This decision
narrows ADR 0023 § Consequences: "bot links" means URLs that a user chooses, which still go through `EgressPolicy`.

## Considered Options

- **Fetch through `EgressPolicy` with the pinned HttpClient 5 client**: uniform with ADR 0022 and ADR 0025, but needs a
  second fetcher or a refactor of the HTML-shaped article adapter behind a new port. Without a host list it admits any
  public host and does not show that the file comes from Discord.
- **Host list, then the egress-checked client**: defense in depth against Discord itself redirecting to a private
  address, at the cost of both of the other options.
- **JDA with a custom OkHttp `Dns` through `EgressPolicy`**: ADR 0025 rejected an SSRF control in `okhttp3`, because
  two OkHttp jars ship that package.

## Consequences

- The guard test gains JDA/OkHttp download markers and allows only the one Discord downloader file by exact path.
- JDA's client follows redirects; the host check covers the first hop only. If Discord's CDN is seen to redirect, or
  a URL that is not a Discord attachment can reach the downloader, the download moves to an egress-checked client.
- A bot feature that takes a free URL from a user (a pasted link) still goes through `EgressPolicy`.
- Platform files in Phase 19 (Slack) follow the same rule: the platform's client, a fixed host list, a capped read.

Decided in [D07](../factory/phase-18/decisions/D07-attachment-download-and-egress.md) by the dark-factory run
`phase-18`.
