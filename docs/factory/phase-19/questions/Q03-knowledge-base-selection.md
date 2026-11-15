---
id: Q03
status: decided
decision: D03
impact: high
blocks: [W04, W05, W06, W07]
---
## Question
How does a Slack command (`/mf-ask`, `/mf-quiz` start, the file upload) name the knowledge base it acts on, given that
Slack slash commands carry only one free-text argument and have no autocomplete?

## Context
- ADR 0030 (Discord): each command takes a required `kb` option; autocomplete shows names and sends the `kbId`; the
  value is untrusted, parsed as UUID and checked with `KnowledgeBaseService.requireOwner`; never a name (names are not
  unique per owner); no stored "current knowledge base". It rejected "the user's only or oldest knowledge base,
  implicitly" and "a stored current knowledge base".
- Slack slash commands send `text` only. Typed choices need Block Kit (a modal opened with the `trigger_id`, with a
  `static_select` of the user's knowledge bases whose values are ids) — an extra round trip per command.
- A bot-provisioned account cannot sign in to the web (ADR 0027) and no bot command creates a knowledge base, so a
  Slack user owns exactly the one provisioned "Slack" knowledge base until account linking (#43) or an operator CLI
  action. A Slack user never sees a `kbId` anywhere.
- `ExternalIdentityService.provision` already creates the platform-named knowledge base (`ExternalPlatform.label()`).

## Known options
- A: Block Kit modal: the slash command opens a modal with a select of owned knowledge bases (value = id) and the
  input (question / file note); submission is checked with `requireOwner`. Closest to ADR 0030.
- B: Implicit when the user owns exactly one knowledge base; with more than one, a modal or select (A) is shown.
- C: The knowledge base is named in the command text (`/mf-ask <name> <question>`); ambiguous names are refused.
  ADR 0030 forbids name values for Discord.
- D: Always the provisioned platform knowledge base (by name or stored id); no selection at all.

## Constraints
- Every reference to a knowledge base is untrusted and checked with `requireOwner(kbId, userId)` before work
  (`docs/standards/security/web-security.md` § API Authorization; ADR 0030).
- Every tenant-scoped port call takes `kbId` first (CLAUDE.md).
- Choosing an implicit or name-based option differs from ADR 0030's Discord rules; it is not a contradiction (ADR 0030
  is scoped to Discord) but it needs its own ADR with the reason.
- Slack needs an ack within 3 s; a modal must be opened with the `trigger_id` within 3 s.
