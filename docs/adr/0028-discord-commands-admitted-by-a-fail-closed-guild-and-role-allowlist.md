# Discord commands are admitted by a fail-closed guild and role allowlist, and never from a DM

Status: Accepted (2026-11-14).

The Discord bot runs a slash command only when it comes from a guild on `mindforge.discord.allowed-guild-ids`. When
the bot token is set, this list must not be empty: an `@AssertTrue` on `AppProperties.Discord` stops startup
otherwise. `mindforge.discord.allowed-role-ids` is optional and flat. When it is empty, any member of an allowed guild
is admitted. When it is not empty, the member must have at least one listed role. Role ids are snowflakes, which are
unique across Discord, so one flat set is exact. A command from a DM has no guild and no roles, and is rejected. A
command from another guild gets a fixed reply that does not name guilds or roles; the bot does not leave that guild.
The check runs first in the command handler, before identity resolution, so a rejected caller provisions no account
and spends no model budget.

## Considered Options

- **Empty guild list admits all guilds**: an unset or mistyped variable makes an open bot. Anyone with the invite
  link gets an account and model spend. This is a blocklist by default; ADR 0022 rejected the same "empty = all" shape.
- **Accept DMs from a user who already has a linked identity**: the link proves a past check, not current
  membership. A kick, a ban or a role removal would not revoke DM access.
- **A role list per guild (`guildId -> roleIds`)**: role ids are already globally unique, so the map adds config shape
  and no guarantee.
- **Leave non-allowed guilds on join**: extra event handling and a failure path, with no gain over rejection. It can
  be added later without a change to these rules.

## Consequences

- An operator must set the guild list with the token; the failure is a startup error with a field-specific message.
- Incoming DMs never run commands. DMs from the bot (reminders) are outbound only. A DM command use needs a new
  decision with its own explicit allowlist.
- The allowlist is a pure value that unit tests drive without JDA.

Decided in [D04](../factory/phase-18/decisions/D04-guild-and-role-allowlist-semantics.md) by the dark-factory run
`phase-18`.
