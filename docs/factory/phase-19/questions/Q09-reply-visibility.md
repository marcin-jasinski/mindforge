---
id: Q09
status: answered
impact: medium
blocks: [W04, W05, W06, W07]
answered_by: docs/adr/0032-discord-replies-are-ephemeral.md#Consequences
---
## Question
Are Slack replies visible to the channel or only to the caller?

## Context
ADR 0032 § Consequences: "Platform bots in Phase 19 (Slack) follow the same rule with the platform's equivalent of an
ephemeral reply." Slack's equivalents: a slash command `response_url` reply with `response_type: ephemeral` (the
default), `chat.postEphemeral`, or a message in the bot's DM with the user.

## Known options
- A (settled): every reply only to the caller; one reply helper in the Slack adapter sets it; no public option.

## Constraints
Replies never carry reference answers or grounding excerpts (CLAUDE.md). The upload path (Q04) must reply privately too.
