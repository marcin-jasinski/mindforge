---
id: P19-07
title: Ingest a file sent to the bot in a direct message as a Slack upload
status: ready
plan_tasks: [19.1, 19.2, 19.3]
decisions: [D04]
depends_on: [P19-04, P19-06]
size: L
commit:
fix_rounds: 0
issue: 51
---
## Goal
A Slack user uploads a document by sending one file to the bot in a direct message. The handler admits the event
(team allowlist on the event's team and the caller's `user_team`), counts it on the rate limit, resolves the user,
takes the only knowledge base, downloads the file through `SlackFiles` and calls `IngestionService.ingest` with
`UploadSource.SLACK` (new, plan 19.1). One reply ("queued" or a fixed refusal) goes to the same DM. Slack uploads are
scanned for article links like other user uploads.

## Context
- P19-03: `SlackAllowlist`, `SlackRateLimiter`, the gate and the reply seam; D01 adapter rule: resolve with
  `ExternalAccounts.slackExternalId(callerTeamId, userId)` using the same caller team the allowlist checked.
- **D02 for events (binding; applied in P19-03):** workspace = event `team` (or envelope `team_id`); caller = event
  `user_team`; null or blank → reject. Check first, before rate limit, resolution and download. A rejection is a fixed
  text naming no workspace, logged at INFO with a reason code.
- P19-04: `SlackKnowledgeBases` (only knowledge base + `requireOwner`, fixed refusal). P19-06: `SlackFiles`
  (https, `files.slack.com`, bearer token, declared-size check, capped read, rejection reasons).
- `src/main/java/dev/mindforge/domain/model/UploadSource.java` ends with `DISCORD`. Phase-15 D06: a new source lands
  with its producer. `ArticleFetcher.SCANNED_SOURCES = {API, CLI, FILE_WATCHER, DISCORD}`
  (`src/main/java/dev/mindforge/application/service/ArticleFetcher.java:43`). Q15 (Phase 18 D12): add `SLACK` to
  `SCANNED_SOURCES` with a unit test in `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java`;
  `ARTICLE` and `CONVERSATION` stay out. Check whether `upload_source` has a DB CHECK constraint or a mapped enum
  column that needs a migration; record the finding in Notes.
- Mirror, do not import: `src/main/java/dev/mindforge/discord/UploadCommand.java` (refusal texts lines 34-44,
  exception mapping: `LessonAlreadyExistsException`, `UnknownLessonException`, `LessonIdentityException`,
  `UploadRejectedException`, `KnowledgeBaseBusyException`; `mediaType()` strips parameters; queued reply
  "Document <id> accepted; its ingest run is queued. …") and `UploadCommandTest`.
- Q09 (ADR 0032): the reply is private; a DM with the bot is private, so a `chat.postMessage` into that DM channel is
  the reply (through the one Slack reply seam). Ack the event first; do the work off the ack path on the virtual-thread
  executor. No transaction in the adapter.
- ADR 0035 (`docs/adr/0035-slack-uploads-are-files-sent-to-the-bot-in-a-direct-message.md`) is already written.

## Decisions to apply
**D04 — Decision:** Use A: a Slack user uploads a document by sending one file to the bot in a direct message (App Home
messages tab). The handler acts on a `message` event with subtype `file_share` in an `im` channel only; files in
channels are ignored silently. The message text may carry `lesson_id=<value>` and `new_version`. The knowledge base is
the one chosen per Q03 (the user's only knowledge base). The single reply ("queued" or a fixed refusal) goes to the same
DM.

**D04 — Implementation notes:**
- `dev.mindforge.slack.SlackUploadHandler`, registered on Bolt `message` events (and `MessageFileShareEvent` if Bolt
  routes subtype `file_share` separately). Act only when `channel_type == "im"`, `subtype == "file_share"`, `bot_id`
  is null. Ignore other messages silently. Ack first; do the work off the ack path.
- Exactly one file per message; more -> fixed text "Send one file per message.".
- Text grammar in a package-private `SlackUploadText.parse(String)` returning
  `record UploadOptions(String lessonId, boolean newVersion)`: tokens `lesson_id=<value>` and `new_version`,
  case-insensitive key. An unknown token or an empty `lesson_id=` -> fixed refusal naming the grammar.
- Gate order (as Discord): allowlist (Q02) -> rate limit (one count per upload, D11) ->
  `ExternalIdentityService.resolve(ExternalPlatform.SLACK, ...)` -> knowledge base (Q03) + `requireOwner` ->
  size check from `file.size` -> capped download (Q08) -> `IngestionService.ingest(new DocumentUpload(kbId, userId,
  UploadSource.SLACK, name, mediaType, bytes, lessonId, newVersion))` -> one DM reply.
- Refusal texts: copy the `UploadCommand` constants (34-44), change "Discord's CDN" to Slack wording; never send an
  exception message.
- Reply through the single Slack reply helper (Q09) into the DM channel.
- Scopes `im:history`, `files:read`, `chat:write`; event subscription `message.im`; App Home messages tab enabled;
  record these in deployment.md (done in P19-08).
- `UploadSource.SLACK` appended after `DISCORD` (plan 19.1).
- Unit tests: channel file ignored; bot message ignored; non-`file_share` DM ignored; two files refused; bad text
  refused; `lesson_id` and `new_version` passed through; allowlist and rate limit run before any download; a foreign
  knowledge base refused; a redelivered event gives no second run (ingest dedup).

## Acceptance criteria
- [ ] AC1: `UploadSource.SLACK` is appended after `DISCORD`; `ArticleFetcher.SCANNED_SOURCES` contains `SLACK`;
  `ARTICLE` and `CONVERSATION` stay out.
- [ ] AC2: Only a `message` event with `channel_type == "im"`, subtype `file_share` and no `bot_id` is handled; a
  channel file, a bot message and a DM without a file are ignored silently (no reply, no service call).
- [ ] AC3: The event is admitted only when `SlackAllowlist.admits(event team, event user_team)`; otherwise the DM reply
  is "This bot is not available here." and neither the rate limiter, `ExternalIdentityService`, `SlackFiles` nor
  `IngestionService` is called.
- [ ] AC4: Over the rate limit, the reply is "Rate limit reached. Try again in N s." and nothing is resolved or
  downloaded; each upload counts once.
- [ ] AC5: Identity resolution uses `slackExternalId(user_team, user)`; with zero or several knowledge bases, or a
  failed `requireOwner`, the fixed knowledge-base refusal is sent and nothing is downloaded.
- [ ] AC6: Two or more files → "Send one file per message."; `SlackUploadText.parse` accepts `lesson_id=<value>` and
  `new_version` (case-insensitive keys) and refuses an unknown token or an empty `lesson_id=` with a fixed text naming
  the grammar.
- [ ] AC7: A valid upload calls `IngestionService.ingest(new DocumentUpload(kbId, userId, UploadSource.SLACK, name,
  mediaType, bytes, lessonId, newVersion))` with the bare media type, and replies with the queued text.
- [ ] AC8: Too large (declared or read), not from Slack, download failure, lesson exists, unknown lesson, bad lesson
  identity, rejected upload and busy knowledge base each get their fixed text; no exception message is sent.
- [ ] AC9: A redelivered event for the same file returns the existing document and starts no second ingest run
  (ingest dedup), and the handler does not fail.
- [ ] AC10: `SlackConfig` registers the message handler; `SlackConfigTest` still shows no Slack bean without tokens or
  in `cli`; `OutboundHttpGuardTest` stays green.

## Test plan
- AC1: unit `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java` (SLACK upload is scanned).
- AC2–AC8: unit `src/test/java/dev/mindforge/unit/slack/SlackUploadHandlerTest.java` (Mockito `IngestionService`,
  `ExternalIdentityService`, `SlackKnowledgeBases`, `SlackFiles`; recording reply double; `make*` event factories,
  Bolt-free event record if the handler translates the Bolt event first) and
  `src/test/java/dev/mindforge/unit/slack/SlackUploadTextTest.java`.
- AC9: unit test that the second identical ingest returns the same document id and the handler replies once per event
  (dedup is `IngestionService`'s; mock its contract) — or an integration test if the Discord upload has one.
- AC10: integration-tagged `SlackConfigTest`; existing `OutboundHttpGuardTest`.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/UploadSource.java`
- `src/main/java/dev/mindforge/application/service/ArticleFetcher.java`
- `src/main/java/dev/mindforge/slack/SlackUploadHandler.java` (new)
- `src/main/java/dev/mindforge/slack/SlackUploadText.java` (new)
- `src/main/java/dev/mindforge/slack/SlackConfig.java`, `SlackReply` implementation (DM channel reply)
- `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java`
- `src/test/java/dev/mindforge/unit/slack/SlackUploadHandlerTest.java` (new), `SlackUploadTextTest.java` (new)
- `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`

## Out of scope
- Files in channels, `channels:history`, a third slash command, modals.
- Any change to `UploadPolicy`/`UploadSanitizer` limits or to the Discord upload.
- Deployment docs for scopes (P19-08).

## Notes
