---
id: D04
question: Q04
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q04.r1.tally.md]
adr: docs/adr/0028-discord-commands-admitted-by-a-fail-closed-guild-and-role-allowlist.md
---
## Decision
Use A: fail closed. When the bot token is set, `mindforge.discord.allowed-guild-ids` must be non-empty, or startup
fails. `allowed-role-ids` is optional and flat: empty means any member of an allowed guild; non-empty means the
member needs at least one listed role. A command from a DM (no guild) is rejected. A command from a guild that is not
on the list is rejected with a fixed reply; the bot does not leave that guild. The check runs first in the command
handler, before identity resolution, so a rejected caller provisions nothing.

## Why
- validation.md:15 "Allowlists Over Blocklists" and scope.md:118-119 "fail closed" rule out an empty list that admits all (C).
- ADR 0022:17 already rejected "empty = all public hosts" for the same reason; consistent precedent.
- `AppProperties` is `@Validated` with `@AssertTrue` cross-field checks (AppProperties.java:21, :91), so fail-fast costs one method.
- Q08: the bot exists only when the token is set, so the guard is "token blank OR guild list non-empty".
- A DM carries no guild and no member roles, so no current check is possible; accepting linked users (B) keeps access after a kick or role removal.
- Role snowflakes are globally unique, so a flat set is exact; a per-guild map (D) adds shape without a guarantee.

## Rejected
- B: DM access survives guild or role revocation; the allowlist stops being the access boundary.
- C: open by default; a missing env var makes a public bot that provisions accounts and spends model budget.
- D: per-guild role map is speculative generality; flat set of unique ids says the same.
- E (= skeptic's A2): leaving non-allowed guilds adds JDA event handling for no security gain; can be added later.

## Dissent
None (unanimous). Risk to watch: an operator who wants DM use will ask for it; the answer is a separate explicit
DM-user allowlist decided later, never "linked once = allowed".

## Implementation notes
- `AppProperties.Discord` (nested, `@Valid` on the root field): `String botToken`; `Set<Long> allowedGuildIds = Set.of()`;
  `Set<Long> allowedRoleIds = Set.of()`. Binding to `Long` makes a non-numeric id a startup error.
- `@AssertTrue(message = "allowed-guild-ids must not be empty when bot-token is set") public boolean isGuildAllowlistSetWhenEnabled()`
  returns `botToken == null || botToken.isBlank() || !allowedGuildIds.isEmpty()`.
- Env vars in `deployment.md` and `env.example`: `MINDFORGE_DISCORD_ALLOWED_GUILD_IDS` (required with the token),
  `MINDFORGE_DISCORD_ALLOWED_ROLE_IDS` (optional), comma-separated.
- JDA-free value in the Discord adapter package (package per Q09): `record DiscordAllowlist(Set<Long> guildIds, Set<Long> roleIds)`
  with `boolean admits(Long guildIdOrNull, Set<Long> memberRoleIds)`: null guild -> false; guild not in set -> false;
  `roleIds` empty -> true; else `!Collections.disjoint(roleIds, memberRoleIds)`. Build it from properties in the Discord `@Configuration`.
- Slash handler: call `admits(...)` first, before any identity resolution, `requireOwner` or model work. On false,
  reply ephemeral fixed text "This bot is not available here." and return. Log at INFO `Discord command rejected: guild={}`
  (guild id only, or "dm"); never log or echo the lists or the token.
- Register commands with guild-only context (JDA `InteractionContextType.GUILD`) so DMs do not offer them; the
  `admits` check stays the authoritative boundary.
- Tests (18.4): `DiscordAllowlistTest` — DM rejected; other guild rejected; allowed guild + empty role list admitted;
  required role missing rejected; one matching role admitted. Handler test: non-allowed guild -> fixed reply and the
  external-identity port / identity service is never called. `AppProperties` validation test: token set + empty guild
  list fails; no token + empty list passes.
- DMs stay outbound only (reminders, 18.3); document this in `deployment.md`.
