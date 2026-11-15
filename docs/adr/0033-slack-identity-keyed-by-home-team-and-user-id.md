# Slack identity is keyed by the caller's home team and user id

Status: Accepted (2026-11-15).

For `ExternalPlatform.SLACK`, the `external_id` stored in `external_identities` is the caller's home team id and Slack
user id joined by a hyphen: `T0123ABCD-U0456EFGH`. The home team is `user_team` in a slash-command payload or
`user.team_id` in an event, and the payload `team_id` only when these are absent; it is the same team the Slack
allowlist checks. Slack user ids are unique per workspace only, and the allowlist admits a list of teams, so a key
without the team could link two people to one account and its knowledge bases. The synthetic email follows ADR 0027:
`slack-t0123abcd-u0456efgh@users.invalid`. `ExternalAccounts` builds the key (`slackExternalId`) and admits the hyphen
form for SLACK only; Discord keeps the letters-and-digits rule.

## Considered Options

- **The user id alone**: Discord's shape and no rule change, but unique only while one workspace is admitted; a
  collision gives one person another person's account, and nothing reports it.
- **Team id and user id with no separator**: no rule change, but the split exists only by convention, so the user id
  cannot be recovered from the key for a later DM or a per-team cleanup.
- **Team id and user id with `:`**: `:` is not legal in an unquoted email local part, so the ADR 0027 address breaks.
- **The Enterprise Grid id when present, else the team id**: one account per Grid org, but the key changes when a
  workspace joins an org, which re-provisions every user as a new empty account.

## Consequences

- An Enterprise Grid user in two admitted workspaces gets two accounts. This is safe and accepted.
- The key fits `VARCHAR(64)`; no migration. The same `ExternalIdentityRepository` serves Discord and Slack.
- Every Slack code path builds the key through `ExternalAccounts.slackExternalId`, never by string concatenation.
- An operator can find the accounts of one team by the `T…-` prefix of `external_id`.

Decided in [D01](../factory/phase-19/decisions/D01-slack-identity-key.md) by the dark-factory run
`phase-19`.
