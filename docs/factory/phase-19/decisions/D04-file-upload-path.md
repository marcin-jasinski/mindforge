---
id: D04
question: Q04
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q04.r1.tally.md]
adr: docs/adr/0035-slack-uploads-are-files-sent-to-the-bot-in-a-direct-message.md
---
## Decision
Use A: a Slack user uploads a document by sending one file to the bot in a direct message (App Home messages tab).
The handler acts on a `message` event with subtype `file_share` in an `im` channel only; files in channels are
ignored silently. The message text may carry `lesson_id=<value>` and `new_version`. The knowledge base is the one
chosen per Q03 (the user's only knowledge base). The single reply ("queued" or a fixed refusal) goes to the same DM.

## Why
- ADR 0032:8-10,28: knowledge bases are private and a posted message cannot be recalled; a DM keeps both the file and
  the reply private, so no `chat.postEphemeral` is needed.
- Plan 19.2 (implementation-plan.md:1653) names a "file upload handler", not a third command; scope.md:31 excludes
  commands outside the plan.
- A Slack user owns exactly one provisioned knowledge base until #43 (scope.md:64-65), so no picker or modal is needed.
- It keeps the one-step shape of `discord/UploadCommand.java` (same `lesson_id`/`new_version`, same fixed refusals).
- Only `im:history` is needed, not `channels:history`: the bot does not read channel messages.

## Rejected
- B: a channel file is public before the bot acts (ADR 0032); needs `channels:history`.
- C: two-step modal flow; the bot cannot fetch a file in a channel it is not in, so it fails late.
- D: a third slash command outside the plan; two-step modal flow; `file_input` size cap may differ from 50MB.

## Dissent
None. Risk to watch: A cannot open a modal (a message event has no `trigger_id`); if Q02 rejects DMs or Q03 picks a
modal, this decision must be reopened. The free-text grammar is new parse surface; keep it to two tokens.

## Implementation notes
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
  record these in deployment.md.
- `UploadSource.SLACK` appended after `DISCORD` (plan 19.1).
- Unit tests: channel file ignored; bot message ignored; non-`file_share` DM ignored; two files refused; bad text
  refused; `lesson_id` and `new_version` passed through; allowlist and rate limit run before any download; a foreign
  knowledge base refused; a redelivered event gives no second run (ingest dedup).
