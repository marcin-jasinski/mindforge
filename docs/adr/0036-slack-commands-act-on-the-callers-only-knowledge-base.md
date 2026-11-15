# Slack commands act on the caller's only knowledge base

Status: Accepted (2026-11-15).

`/mf-ask`, `/mf-quiz start` and the Slack file upload name no knowledge base. The handler calls
`KnowledgeBaseService.list(userId)` for the resolved caller. When the list has exactly one knowledge base, the handler
takes its `kbId`, calls `KnowledgeBaseService.requireOwner(kbId, userId)`, and then does the work with that `kbId`
first on every port call. When the list is empty or has more than one knowledge base, the handler sends one fixed
ephemeral reply and does no work; it never picks one. The command text carries no knowledge-base token, names are never
parsed, and there is no stored "current knowledge base". Slack slash commands send one free-text argument and have no
autocomplete, so the Discord shape (ADR 0030: a required `kb` option whose value is an id) has no Slack equivalent
without a modal. A Slack user cannot own a second knowledge base in Phase 19: provisioning creates exactly one, named
"Slack" (ADR 0030), a bot account cannot sign in to the web (ADR 0027), and no bot or CLI command creates a knowledge
base. A selector would always show one item. ADR 0030 stays the rule for Discord.

## Considered Options

- **A Block Kit modal on every command (a `static_select` of owned knowledge bases, value = id)**: closest to ADR 0030,
  but it adds a `view_submission` handler, `private_metadata` state and a second round trip per command, the modal must
  open within the 3 s `trigger_id` window after provisioning, and a file-upload event has no `trigger_id`. Today it
  selects from a list of one.
- **Implicit with one knowledge base, a modal with several**: ships all of the modal code for a branch no user can reach
  before account linking, so its faults show only in production.
- **The knowledge-base name in the command text (`/mf-ask <name> <question>`)**: names are not unique per owner, and a
  free-text split has no delimiter, so a name with spaces or a name that is a prefix of another can route work into
  the wrong knowledge base.
- **Always the provisioned platform knowledge base, by name or by a stored id**: by name has the same defect; a stored
  id is the stale pointer and migration that ADR 0030 rejected.

ADR 0030 rejected "the user's only or oldest knowledge base, implicitly" because a user with several gets work in a
knowledge base they did not pick, or is locked out. This rule never picks among several (it refuses), and in Phase 19
the several case cannot occur.

## Consequences

- One resolver in the Slack adapter does the list, the size check and `requireOwner`; handlers do not call `list`
  themselves. A knowledge base deleted between `list` and the check gets the same fixed reply.
- `/mf-quiz answer` takes no knowledge base; it uses the one stored with the quiz session.
- Account linking ([#43](https://github.com/marcin-jasinski/mindforge/issues/43)) is the first way a Slack user can own
  a second knowledge base. #43 must add a Slack selection (a Block Kit select whose value is the `kbId`, checked with
  `requireOwner`) before, or with, the change that makes a second knowledge base reachable. Replacing the refusal
  branch with that selection is additive: the command syntax does not change.
- An operator action that gives a Slack user a second knowledge base locks that user out of Slack commands until the
  selection exists; the fixed reply makes the cause visible.

Decided in [D03](../factory/phase-19/decisions/D03-knowledge-base-selection.md) by the dark-factory run
`phase-19`.
