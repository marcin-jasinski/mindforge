# Slack commands are admitted when both the workspace and the caller's own team are on a fail-closed team allowlist

Status: Accepted (2026-11-15).

The Slack bot handles a slash command or an event only when two team ids are on `mindforge.slack.allowed-team-ids`:
the workspace the payload comes from (`team_id`, or the event's `team`), and the caller's own team (the event's
`user_team`; for a slash command, `user_team` when present, else `team_id`, because Slack runs slash commands only for
members of the installing workspace). When either id is missing, blank or not listed, the caller is rejected. When the
Slack tokens are set, the list must not be empty: an `@AssertTrue` on `AppProperties.Slack` stops startup otherwise.
`enterprise_id` is not used for admission; an Enterprise Grid operator lists each workspace. Commands from a DM are
admitted, because a Slack DM carries both team ids and the check runs on every call. A rejected caller gets a fixed
reply that does not name workspaces. The check runs first in each handler, before rate limit, identity resolution and
provisioning, so a rejected caller provisions no account and spends no model budget.

## Considered Options

- **Check the payload `team_id` only**: with one install, `team_id` is the same for every event. A member of another
  organisation in a Slack Connect shared channel can post a file; the event carries the host `team_id`, so this check
  admits them and auto-provisions an account with model spend.
- **Reject DMs, as ADR 0028 does for Discord**: ADR 0028 rejects DMs because a Discord DM has no guild and no roles, so
  membership cannot be checked. A Slack DM carries both team ids, so the reason does not apply; rejecting DMs would also
  push private uploads into channels.
- **An optional channel allowlist (the analogue of Discord roles)**: no requirement asks for channel scoping. It can be
  added later without a change to these rules.
- **Admit by `enterprise_id`**: admits every workspace of a Grid organisation, and the id is absent outside Grid.
- **Resolve the caller's team with `users.info`**: an API call and a failure path before the gate, for an id the event
  payload already carries.

## Consequences

- An operator must set the team list with the tokens; the failure is a startup error with a field-specific message.
- The allowlist is a pure value (`SlackAllowlist`) that unit tests drive without Bolt.
- A new Slack payload type that has no caller team is rejected until a new decision says how to find that team.
- This is the explicit allowlist for DM use that ADR 0028 § Consequences asks for, for Slack only; Discord DMs stay
  rejected.

Decided in [D02](../factory/phase-19/decisions/D02-workspace-allowlist-semantics.md) by the dark-factory run
`phase-19`.
