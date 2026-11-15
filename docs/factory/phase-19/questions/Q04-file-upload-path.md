---
id: Q04
status: decided
decision: D04
impact: high
blocks: [W07]
---
## Question
How does a Slack user upload a document — which Slack event or interaction triggers the "file upload handler", where
may it happen, and how are the knowledge base, `lesson_id` and `new_version` given?

## Context
- Plan 19.2: "file upload handler" (not a slash command); architecture.md:113: "file upload".
- Slack slash commands cannot carry a file. The usual triggers are: a `message` event with subtype `file_share` (or
  the `file_shared` event) in a channel or DM where the bot is a member (scopes `files:read`, `channels:history` /
  `im:history`); a message shortcut ("Upload to MindForge") on a message that holds a file; or a modal with a
  `file_input` block.
- A file shared in a channel is visible to every channel member; a file sent in a DM to the bot (App Home messages tab)
  is private. ADR 0032 makes every Discord reply ephemeral because knowledge bases are private.
- Discord precedent (`discord/UploadCommand.java`): options `kb`, `file`, `lesson_id`, `new_version`; one reply that
  says the run is queued; fixed texts for each refusal.
- Bytes are fetched from `url_private_download` with the bot token as bearer (Q08).
- Interacts with Q02 (DMs admitted?) and Q03 (knowledge base selection).

## Known options
- A: DM the bot with the file; the message text may carry `lesson_id` / `new_version`; the knowledge base per Q03;
  the reply goes to the DM. Files in channels are ignored.
- B: Any `file_share` message in a channel or DM where the bot is a member and the bot is mentioned; reply
  ephemeral (`chat.postEphemeral`) to the uploader.
- C: A message shortcut on a message with a file opens a modal (knowledge base select, lesson id, new version), then
  uploads; works in any channel the user can see.
- D: A `/mf-upload` slash command opens a modal with a `file_input` block, knowledge base select and lesson fields.

## Constraints
- Uploads pass `UploadPolicy`/`UploadSanitizer` with the same size and MIME limits as every channel
  (`AppProperties.Upload`); the download is capped (ADR 0029 § Consequences).
- `UploadSource.SLACK` is added with this producer (plan 19.1; phase-15 D06).
- Allowlist (Q02), rate limit (one count per upload, D11) and identity resolution run before any download.
- Replies follow ADR 0032 (the platform's ephemeral or private equivalent); refusals use fixed texts.
- Minimal implementation: one upload path, not several.
