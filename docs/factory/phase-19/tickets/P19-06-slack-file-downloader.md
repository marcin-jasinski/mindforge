---
id: P19-06
title: Download a Slack file only from files.slack.com with the bot token and a capped read
status: ready
plan_tasks: [19.2]
decisions: []
depends_on: [P19-02]
size: S
commit:
fix_rounds: 0
issue: 50
---
## Goal
`dev.mindforge.slack.SlackFiles` is the only class in `dev.mindforge.slack` that does HTTP. It reads a Slack file's
`url_private_download` with the bot token as a bearer header, only over https from a fixed host list
(`files.slack.com`), checks the declared size first and reads at most max+1 bytes. `OutboundHttpGuardTest` allows that
one file by exact path. The upload handler (P19-07) uses it.

## Context
- P19-02 added the Slack SDK (`slack-api-client` uses OkHttp; `okhttp-jvm` 5 order in `pom.xml`, ADR 0025) and
  `AppProperties.Slack.botToken`.
- Q08 is settled by ADR 0029 § Consequences ("Platform files in Phase 19 (Slack) follow the same rule: the platform's
  client, a fixed host list, a capped read."): one Slack downloader, using the Slack SDK's OkHttp client; before any
  request the URL must be `https`, default port (or 443), no userinfo, host in a fixed list (`files.slack.com`); the
  declared size is checked against `mindforge.upload.max-size` and the body is read to at most max+1 bytes; then
  `UploadPolicy` admits name, MIME and size (in `IngestionService`). A user-chosen URL would go through `EgressPolicy`
  (ADR 0023); this is not one. A deviation (other host or client) needs a new ADR.
- The bot token is a secret: it is sent only to the fixed host list and never logged; the URL's query is never logged
  (log the host only). A redirect to another host must not carry the token (OkHttp drops `Authorization` on a
  cross-host redirect): test it, or disable redirects and treat a redirect as a download failure — record which in Notes.
- Mirror, do not import: `src/main/java/dev/mindforge/discord/DiscordAttachments.java` (`requireCdn` URL check,
  `MAX_ARRAY_LENGTH`, timeout, `readNBytes(min(MAX, max+1))`), `AttachmentRejectedException.java` (reasons
  `NOT_FROM_DISCORD`, `TOO_LARGE`, `DOWNLOAD_FAILED`), test `src/test/java/dev/mindforge/unit/discord/DiscordAttachmentsTest.java`.
- `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java:36-40`: `OPERATOR_ENDPOINT_FILES`
  lists `SecurityConfig.java` and `DiscordAttachments.java` by exact path; markers include `okhttp3`, `.download(`,
  `.getProxy()`. Add exactly `dev/mindforge/slack/SlackFiles.java` with a comment citing ADR 0029.
- Domain exceptions with meaningful bases; fixed texts are chosen by the caller from the reason, never the message.
  No test connects to Slack: use OkHttp `MockWebServer` only if it is already a test dependency, otherwise a seam over
  the OkHttp call; never a real Slack host.

## Decisions to apply
None of D01–D07/D16 directly. Binding answered question Q08 (ADR 0029 § Consequences), copied in Context.

## Acceptance criteria
- [ ] AC1: A URL that is not https, has userinfo, a non-default port, or a host outside `files.slack.com` is refused
  with reason "not from Slack" before any request is made (no token sent).
- [ ] AC2: A declared size over the max is refused as too large before any request; a body longer than the max is
  refused as too large after reading at most max+1 bytes.
- [ ] AC3: A good URL is requested with `Authorization: Bearer <bot token>` and returns the bytes.
- [ ] AC4: A network error, a non-2xx status or a timeout is refused as a download failure; the log line has the host
  only, never the token or the query.
- [ ] AC5: A redirect to another host never carries the bot token (test, or redirects disabled and refused).
- [ ] AC6: `OutboundHttpGuardTest` allows `dev/mindforge/slack/SlackFiles.java` by exact path and still fails for any
  other file under `dev/mindforge/slack` that holds an HTTP marker (add a case or reuse the generic one).
- [ ] AC7: `SlackFiles` is built in `SlackConfig` with the bot token and the upload max size.

## Test plan
- AC1–AC5: unit `src/test/java/dev/mindforge/unit/slack/SlackFilesTest.java` (no network to Slack; local fake server or
  an injected call seam; `make*` helpers).
- AC6: unit `OutboundHttpGuardTest` (extend).
- AC7: integration-tagged `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`.

## Files likely touched
- `src/main/java/dev/mindforge/slack/SlackFiles.java` (new)
- `src/main/java/dev/mindforge/slack/SlackFileRejectedException.java` (new; name is the implementer's choice)
- `src/main/java/dev/mindforge/slack/SlackConfig.java`
- `src/test/java/dev/mindforge/unit/slack/SlackFilesTest.java` (new)
- `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java`
- `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`

## Out of scope
- The message event handler, `UploadSource.SLACK`, `ArticleFetcher` (P19-07).
- `EgressPolicy` or HttpClient 5 for this download (rejected by ADR 0029).
- Hosts other than `files.slack.com`; a new ADR.

## Notes
