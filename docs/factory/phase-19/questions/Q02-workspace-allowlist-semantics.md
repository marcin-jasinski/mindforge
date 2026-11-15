---
id: Q02
status: decided
decision: D02
impact: high
blocks: [W02, W04]
---
## Question
What exactly does the Slack workspace allowlist admit: which ids from the payload are checked (team, the caller's own
team, enterprise), are callers from other organisations in shared (Slack Connect) channels admitted, and are commands
from a DM admitted?

## Context
- Plan 19.2: "workspace allowlist". No more detail.
- Discord precedent (ADR 0028): fail-closed `allowed-guild-ids`, required when the token is set (`@AssertTrue` on
  `AppProperties.Discord`), optional role list, DMs rejected because a DM has no guild; the check runs before identity
  resolution and rate limit.
- Slack differs: a Socket Mode app with one bot token is installed in one workspace, so the payload `team_id` is that
  workspace for every event; a slash command in a DM still carries `team_id`. In a Slack Connect shared channel, a
  member of another organisation can run the slash command; the payload's `user.team_id` / `user_team` then differs
  from the installing workspace. Enterprise Grid adds `enterprise_id`. Slack has no role ids; the nearest are user
  groups (needs `usergroups:read` and an API call).
- If only `team_id` is checked, an external Slack Connect user passes the allowlist and is auto-provisioned an account
  with model spend.

## Known options
- A: Required, fail-closed `mindforge.slack.allowed-team-ids`; admit when the payload's `team_id` is listed. DMs
  admitted (they carry the team). External users in shared channels admitted.
- B: As A, and also require the caller's own team (`user.team_id` / `user_team`, or `users.info`) to be listed, so
  Slack Connect guests from other organisations are rejected. DMs admitted.
- C: As B, and reject DMs (commands only from channels), mirroring ADR 0028 literally.
- D: As B, plus an optional channel allowlist (the Slack analogue of Discord's role list).

## Constraints
- Fail closed; never "empty = all" (ADR 0028 § Considered Options; ADR 0022; `docs/standards/global/validation.md`);
  startup fails when the list is empty and the tokens are set.
- The check runs first, before rate limit, identity resolution or any provisioning (ADR 0028 rationale; D11 order).
- A rejected caller gets a fixed reply that does not name workspaces (ADR 0028).
- The allowlist should be a pure value unit tests drive without Bolt (ADR 0028 § Consequences; plan 19.3).
- A new ADR records the Slack semantics in the newest ADR format.
