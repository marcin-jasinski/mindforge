# Slack uploads are files sent to the bot in a direct message

Status: Accepted (2026-11-15).

A Slack user uploads a document by sending one file to the MindForge bot in a direct message (the App Home messages
tab). The bot acts on a `message` event with subtype `file_share` in an `im` channel and on nothing else: files shared
in channels are ignored, and the bot does not ask for `channels:history`. The message text may carry
`lesson_id=<value>` and `new_version`; the knowledge base is the user's own (the Slack commands' rule). The bot sends one
reply, "queued" or a fixed refusal, in the same direct message. Knowledge bases are private (ADR 0032): a file posted in
a channel is shown to every member before the bot can act, and a posted message cannot be recalled. A direct message
keeps both the file and the reply private.

## Considered Options

- **Any `file_share` message that mentions the bot, with an ephemeral reply**: the file itself is already public in the
  channel, and the bot must read every channel message (`channels:history`).
- **A message shortcut that opens a modal**: two interactions and modal state, and the bot cannot fetch a file in a
  channel it is not a member of, so the user sees the failure only after the modal.
- **A `/mf-upload` command with a `file_input` modal**: a command the plan does not name, a two-step flow, and a Slack
  file size cap that can differ from `mindforge.upload.max-size`.

## Consequences

- A message event has no `trigger_id`, so the upload path cannot open a modal; a knowledge base picker needs a new
  decision (for example after account linking, [#43](https://github.com/marcin-jasinski/mindforge/issues/43)).
- The App Home messages tab must be on, with scopes `im:history`, `files:read` and `chat:write`; deployment.md lists them.
- The allowlist, the rate limit (one count per upload) and identity resolution run before the download; Slack
  redelivers an event that is not acknowledged in 3 s, so the handler acknowledges first and ingest deduplication
  absorbs a redelivery.
- The upload passes the same `UploadPolicy`/`UploadSanitizer` limits as every other channel.

Decided in [D04](../factory/phase-19/decisions/D04-file-upload-path.md) by the dark-factory run
`phase-19`.
