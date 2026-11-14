# Discord replies are ephemeral

Status: Accepted (2026-11-14).

Every reply the Discord bot sends is ephemeral: only the caller sees it. This covers Query answers, quiz questions and
grades, upload results, rejection and error replies, and the deferred reply that precedes a slow answer (the flag is
set on the defer, because Discord does not let it change after the first response). There is no option to post a
reply in the channel. Knowledge bases are private and single-owner (web-security.md § API Authorization; architecture.md
"Tenancy is structural"), and quiz grades are study data; a public reply would show them to every channel member, and
a posted message cannot be recalled. Replies never carry reference answers or grounding excerpts.

## Considered Options

- **Query answers public, quiz and upload ephemeral**: shared learning in the channel, but it publishes content cut from
  a private knowledge base to people who do not own it.
- **A per-command `public:true` option, ephemeral by default**: a caller-controlled path that publishes private wiki and
  study content, and a second mode to test, with no stated requirement.

## Consequences

- Replies are not in the channel history; each `/ask` is a Query interaction (D06), so the answer stays in the web
  Query history.
- One reply helper in the Discord adapter sets the flag; handlers do not reply through JDA directly.
- A later need for shared answers is a new decision with an explicit per-use opt-in, never a public default.
- Platform bots in Phase 19 (Slack) follow the same rule with the platform's equivalent of an ephemeral reply.

Decided in [D14](../factory/phase-18/decisions/D14-reply-visibility.md) by the dark-factory run
`phase-18`.
