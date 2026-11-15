---
id: D02
question: Q02
status: decided
method: unanimous
class: adr
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q02.r1.tally.md]
adr: docs/adr/0034-slack-commands-admitted-when-workspace-and-caller-team-are-allowed.md
---
## Decision
Use B: a required, fail-closed `mindforge.slack.allowed-team-ids`. Admit a slash command or event only when both the
payload workspace (`team_id` / event `team`) and the caller's own team (event `user_team`; for a slash command
`user_team` when present, else `team_id`) are listed. Missing, blank or unlisted -> reject. Ignore `enterprise_id`.
Admit DMs. The check runs first, before rate limit, identity resolution and provisioning; the reply names no workspace.

## Why
- All three ballots agree; A is out on a verified hard objection: on the upload event path (plan 19.2,
  implementation-plan.md:1652-1654) a Slack Connect member of another organisation carries the host `team_id`, so a
  `team_id`-only check provisions them an account and model spend, against ADR 0028:11-12.
- B keeps every ADR 0028 property: fail closed (`@AssertTrue`, AppProperties.java:89-92 shape), pure value
  (DiscordAllowlist.java:10-22 shape), check first, fixed reply.
- ADR 0028 rejects DMs only because a Discord DM "has no guild and no roles" (0028:9); a Slack DM carries both team ids,
  and 0028 § Consequences asks for a new decision with an explicit allowlist for DM use. ADR 0034 is that decision.
- No `users.info` call: the gate stays before any I/O and needs no new scope.

## Rejected
- A: admits Slack Connect guests on the event (upload) path.
- C: copies ADR 0028's DM rule without its reason; blocks private DM uploads.
- D: channel allowlist is speculative (minimal-implementation.md:15); can be added later.
- E (enterprise_id / users.info): too broad, or adds an API call and failure path before the gate.

## Dissent
None on the choice. Skeptic would resolve the slash-command caller team via `users.info` instead of falling back to
`team_id`. Risk to watch: if Slack ever lets external members run slash commands, the `team_id` fallback admits them;
pass `user_team` whenever the payload has it.

## Implementation notes
- `dev.mindforge.slack.SlackAllowlist`: `public record SlackAllowlist(Set<String> teamIds)`, compact ctor
  `teamIds = Set.copyOf(teamIds)`, `public boolean admits(String workspaceTeamIdOrNull, String callerTeamIdOrNull)` —
  true only when both are non-blank and in `teamIds`. Javadoc cites ADR 0034.
- `AppProperties.Slack`: `Set<String> allowedTeamIds = Set.of()` (`mindforge.slack.allowed-team-ids`, env
  `SLACK_ALLOWED_TEAM_IDS`); `@AssertTrue(message = "allowed-team-ids must not be empty when bot-token is set")`, same
  shape as `AppProperties.Discord`.
- Slash commands (`/mf-ask`, `/mf-quiz`): workspace = `team_id`; caller = `user_team` if present, else `team_id`.
  File/message events: workspace = event `team` (or envelope `team_id`); caller = event `user_team`; null -> reject.
  No block-action handlers in Phase 19 (D05).
- Gate order per handler: allowlist -> rate limit -> ack/defer -> identity resolution/provisioning. Rejection reply is a
  fixed ephemeral text naming no workspace; log at INFO with a reason code, never team ids in the reply.
- Tests `SlackAllowlistTest` (plain JUnit, `make*` helpers, AssertJ): listed/listed admits; listed workspace + foreign
  caller team rejected; null and blank caller team rejected; unlisted workspace rejected; DM (same team both ids)
  admitted; `AppProperties.Slack` validation fails with token set and empty list, passes without token.
