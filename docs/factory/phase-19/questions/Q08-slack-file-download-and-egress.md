---
id: Q08
status: answered
impact: medium
blocks: [W07]
answered_by: docs/adr/0029-discord-attachments-downloaded-by-the-platform-client-from-a-fixed-cdn-host-list.md#Consequences
---
## Question
How are the bytes of a Slack file downloaded, and does the download go through `EgressPolicy`?

## Context
- Slack files are read from `url_private_download` (host `files.slack.com`) with the bot token as a bearer header.
- ADR 0029 § Consequences: "Platform files in Phase 19 (Slack) follow the same rule: the platform's client, a fixed
  host list, a capped read." ADR 0023 trusts the operator-configured platform; a user-chosen URL still goes through
  `EgressPolicy`.
- `OutboundHttpGuardTest` allows HTTP-client code only in the article adapter and in exact files listed in
  `OPERATOR_ENDPOINT_FILES` (today `SecurityConfig.java`, `DiscordAttachments.java`).

## Known options
- A (settled by ADR 0029): one Slack downloader class, the only one in `dev.mindforge.slack` that does HTTP, using the
  Slack SDK's OkHttp client; before any request the URL must be `https`, default port, no userinfo, host
  `files.slack.com` (fixed list); the declared size is checked against `mindforge.upload.max-size` and the body is read
  to at most max+1 bytes; then `UploadPolicy` admits name, MIME and size. The guard test allows that one file by exact
  path.
- B: `EgressPolicy` + HttpClient 5 (rejected for the same reasons as in ADR 0029).

## Constraints
- The bot token is a secret: it is sent only to the fixed Slack host list and never logged; the URL's query is not
  logged. A redirect to another host must not carry the token (OkHttp drops `Authorization` on a cross-host redirect;
  test or document it).
- If the implementation deviates (for example a different host or client), it needs a new ADR (ADR 0029 rationale).
