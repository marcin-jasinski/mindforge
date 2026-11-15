---
id: Q04
status: decided
decision: D04
impact: high
blocks: [W04]
---
## Question
What exactly does the guild and role allowlist admit: what happens with an empty list, with a command sent in a DM
(no guild), and how are roles matched?

## Context
Plan 18.2: "Guild and role allowlist via `AppProperties.discord.*`"; 18.4: "non-allowed guilds rejected". Nothing says
whether an unset list means "allow all" or "allow none", whether DMs to the bot (no guild, no roles) may run commands,
or whether the role list is global or per guild (role ids are snowflakes unique across Discord, so a flat list works).
The bot can be added to any guild by anyone with its invite link, so the allowlist is the access boundary for model
spend and for auto-provisioning accounts.

## Known options
- A: Fail closed. `allowed-guild-ids` required non-empty when the bot is on (startup validation fails otherwise);
  `allowed-role-ids` optional — empty means any member of an allowed guild; non-empty means the member needs at least
  one listed role. DMs rejected (no guild to check). The bot leaves or ignores other guilds.
- B: As A, but DMs from a user who already has a linked identity are accepted (they passed the guild check once).
- C: Empty guild list means all guilds (open bot); role list as A. Simplest for a personal deployment, open by default.

## Constraints
- `docs/standards/global/validation.md` § Allowlists Over Blocklists; fail fast on bad config (`AppProperties` is
  `@Validated`).
- The rejection reply must be fixed text and must not reveal configuration.
- The check happens before identity resolution, so a rejected caller provisions nothing.
- The reminder job sends DMs outward; this question is only about incoming commands.
