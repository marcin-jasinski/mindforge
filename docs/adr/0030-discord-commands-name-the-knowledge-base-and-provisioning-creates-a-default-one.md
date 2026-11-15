# Discord commands name the knowledge base, and provisioning creates a default one

Status: Accepted (2026-11-14).

`/ask`, `/quiz start` and `/upload` each take a required `kb` option. Discord autocomplete offers the caller's own
knowledge bases: it shows the name and sends the `kbId`. The value is untrusted, because Discord does not enforce
autocomplete choices. The handler parses it as a UUID and calls `KnowledgeBaseService.requireOwner(kbId, userId)`
before any work; an invalid, unknown or foreign id gets one fixed reply. This is the same shape as the web (id in the
path) and the CLI (`--kb=<id>`, ADR 0019). Names are not unique per owner, so the value is never a name. When a bot
user is provisioned (ADR 0027), one knowledge base named after the platform ("Discord") is created in the same
transaction as the user and the identity link, so `/upload` works on first contact. There is no stored "current
knowledge base".

## Considered Options

- **A stored current knowledge base (`/kb use`)**: needs a fifth command outside the plan's four, a migration, and a
  pointer that goes stale when the knowledge base is deleted.
- **The user's only or oldest knowledge base, implicitly**: a user with several gets uploads and quiz results in a
  knowledge base they did not pick, or is locked out.
- **A guild mapped to a knowledge base by configuration**: members would act in the configured owner's knowledge
  base; there is no sharing or membership model (ADR 0019).
- **An optional `kb` option that defaults to the only knowledge base**: a small UX gain for an extra branch and error
  path. It can be added later without a break, because a required option can become optional.

## Consequences

- The autocomplete handler applies the guild allowlist (ADR 0028) and then resolves or provisions the caller, or a
  first-contact user sees no choices.
- `/quiz answer` takes no `kb`; it uses the knowledge base stored with the quiz session.
- No selection exists for the reminder job to read; it decides from the knowledge bases the user owns.
- A loser of a concurrent first-contact race creates no knowledge base.

Decided in [D03](../factory/phase-18/decisions/D03-which-knowledge-base-a-command-acts-on.md) by the dark-factory run
`phase-18`.
