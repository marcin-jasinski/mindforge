---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W01, W04]
---
## Question
Which Slack value is stored as `external_id` for `ExternalPlatform.SLACK`: the user id alone, or the workspace
(team) id together with the user id?

## Context
- `ExternalIdentityRepository` keys a link by `(platform, external_id)` (`V8__create_external_identities.sql`,
  `external_id VARCHAR(64)`). The value is persisted and becomes part of the synthetic email
  `slack-<externalId>@users.invalid` (ADR 0027, `domain/model/ExternalAccounts.java`), so a later change needs a data
  migration of `external_identities` and `users.email`.
- `ExternalAccounts.syntheticEmail` accepts only `[A-Za-z0-9]+`; a separator such as `:` or `-` between team and user
  would need a change to that rule (and its test `ExternalAccountsTest`).
- Slack user ids (`U…`/`W…`) are documented as unique per workspace; Slack's guidance for apps that may see more than
  one workspace (Enterprise Grid, Slack Connect shared channels) is to key users by `team_id` + `user_id`. Enterprise
  Grid users have global `W…` ids that are the same in every workspace of the org.
- Discord uses the global snowflake alone (`DiscordCommandHandler`: `String.valueOf(call.discordUserId())`).
- Interacts with Q02: if only one workspace can ever be admitted, the user id alone is unique in practice.

## Known options
- A: `user_id` alone (e.g. `U0123ABCD`). Same shape as Discord, no rule change; relies on the allowlist (Q02) to limit
  ids to admitted workspaces. A Grid user is one account across the org's workspaces.
- B: `team_id` and `user_id` concatenated without a separator (`T0123U0456`): unique per workspace, no change to the
  `[A-Za-z0-9]` rule; Slack ids have fixed prefixes, so the split is unambiguous only by convention.
- C: `team_id:user_id` with a separator, widening `ExternalAccounts` to admit `:` (or `-`) for Slack.
- D: `enterprise_id` when present, else `team_id`, plus `user_id` (one account per Grid org).

## Constraints
- ADR 0027: provisioning always inserts and never resolves by email; the synthetic email must stay within 255 chars and
  in `users.invalid`; "Phase 19 reuses the same rule with its own platform prefix".
- `external_id` fits in `VARCHAR(64)`; a schema change needs a new Flyway migration (`docs/standards/backend/migrations.md`).
- Plan 19 checklist: identity resolution reuses the same `ExternalIdentityRepository` as Discord (no Slack-only port).
- Domain stays JDK only.
