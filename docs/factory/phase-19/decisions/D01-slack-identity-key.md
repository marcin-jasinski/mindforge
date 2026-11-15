---
id: D01
question: Q01
status: decided
method: majority
class: adr
votes: { pragmatist: B, architect: C, skeptic: C }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q01.r1.tally.md]
adr: docs/adr/0033-slack-identity-keyed-by-home-team-and-user-id.md
---
## Decision
Use C with a hyphen: for `ExternalPlatform.SLACK`, store `external_id = <caller home team id>-<user id>`
(for example `T0123ABCD-U0456EFGH`). The team id is the caller's own team (`user_team` in a slash-command payload,
`user.team_id` in events; the payload `team_id` only when these are absent), not the channel's team. The synthetic
email is `slack-t0123abcd-u0456efgh@users.invalid`. `ExternalAccounts` admits the hyphen form for SLACK only; the
Discord rule and its test do not change.

## Why
- Slack user ids are unique per workspace only, and `allowed-team-ids` is a list (breakdown W02). `resolve`
  (`ExternalIdentityService`) returns the existing owner of `(platform, external_id)`, so a key without the team can
  give one person another person's account and knowledge bases, silently. A is out.
- `-` is legal `atext` in the email local part; `:` is not. C' is out.
- C over B: both parts stay recoverable (`split("-", 2)`) for a later Slack DM target or a per-team cleanup, and the
  key is readable in the table. The cost is a small, tested domain rule change; no migration (`VARCHAR(64)` holds
  ~23 chars, V8:5).
- D: a key that flips from `T…` to `E…` when a workspace joins a Grid org re-provisions every user; no plan task needs
  Grid org-wide installs.

## Rejected
- A: not unique across admitted workspaces; a silent tenancy breach.
- B: correct today, but the split exists only by convention and the user id cannot be recovered from the key.
- C' (`:`): makes an invalid unquoted email local part, against ADR 0027.
- D: unstable key when a workspace joins a Grid org; speculative scope.

## Dissent
Pragmatist (B): the key is opaque, so a separator and a domain rule change buy nothing. Watch that the hyphen rule
stays SLACK-only and that every Slack code path builds the key through the one domain helper, never by hand.

## Implementation notes
- `domain/model/ExternalAccounts.java`: add
  `private static final Pattern SLACK_ID = Pattern.compile("[A-Za-z0-9]+-[A-Za-z0-9]+");` and pick the pattern by
  platform in `syntheticEmail` (`platform == ExternalPlatform.SLACK ? SLACK_ID : EXTERNAL_ID`); update the Javadoc
  and the exception message.
- Add `public static String slackExternalId(String teamId, String userId)`: each part must match `[A-Za-z0-9]+`, else
  `IllegalArgumentException`; returns `teamId + "-" + userId` (no case change in the stored value).
- `ExternalAccountsTest`: `slackExternalId("T0123","U0456")` -> `"T0123-U0456"`; `syntheticEmail(SLACK, "T0123-U0456")`
  -> `slack-t0123-u0456@users.invalid`; SLACK rejects `"T0123-"`, `"-U1"`, `"T1-U2-U3"`, `"T1:U2"`, `"U0456"` (no team);
  DISCORD still rejects `"12-34"`; `slackExternalId` rejects a blank or non-alphanumeric part.
- Slack adapter (`dev.mindforge.slack`): every `ExternalIdentityService.resolve(ExternalPlatform.SLACK, …)` call uses
  `ExternalAccounts.slackExternalId(callerTeamId, userId)`, after the Q02 allowlist check, with the same caller team id
  the allowlist checks. Unit test: the same user id under two team ids resolves to two different users.
- Reuse `ExternalIdentityRepository` as is; no Flyway migration; no Slack-only port.
- `CONTEXT.md`: if it defines external identity, add the Slack key shape. Link ADR 0033 from `docs/INDEX.md`.
