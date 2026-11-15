---
id: P19-01
title: Add the Slack external platform and its home-team-and-user identity key
status: ready
plan_tasks: [19.2, 19.3]
decisions: [D01]
depends_on: []
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
`ExternalPlatform.SLACK("Slack")` exists. `ExternalAccounts.slackExternalId(teamId, userId)` builds the Slack
external id `<home team id>-<user id>`, and `ExternalAccounts.syntheticEmail` admits that hyphen form for SLACK only.
`ExternalIdentityService.resolve(SLACK, …)` provisions a Slack user and one knowledge base named "Slack" through the
same `ExternalIdentityRepository` as Discord (completion item "Identity resolution reuses the same
`ExternalIdentityRepository` as Discord").

## Context
- `src/main/java/dev/mindforge/domain/model/ExternalPlatform.java` has only `DISCORD("Discord")`; the label names the
  knowledge base that provisioning creates (ADR 0030).
- `src/main/java/dev/mindforge/domain/model/ExternalAccounts.java`: synthetic email
  `<platform>-<externalId>@users.invalid` (ADR 0027); today the external id must match `[A-Za-z0-9]+` (`EXTERNAL_ID`).
- `src/main/java/dev/mindforge/application/service/ExternalIdentityService.java` `resolve(platform, externalId,
  displayName)` already resolves or provisions user + link + one knowledge base for any `ExternalPlatform`
  (race tolerant). Do not change its logic.
- `V8__create_external_identities.sql`: `platform VARCHAR(50)` without CHECK, `external_id VARCHAR(64)`, PK
  `(platform, external_id)`. A new constant and a ~23-char key need **no migration**.
- The domain is JDK only (CLAUDE.md, `docs/standards/architecture/hexagonal.md`). ADR 0033
  (`docs/adr/0033-slack-identity-keyed-by-home-team-and-user-id.md`) is already written; do not write a new ADR.
- ADR 0027 and ADR 0030 bind: provisioning always inserts; a Slack user gets an account that cannot sign in to the web.

## Decisions to apply
**D01 — Decision:** Use C with a hyphen: for `ExternalPlatform.SLACK`, store `external_id = <caller home team
id>-<user id>` (for example `T0123ABCD-U0456EFGH`). The team id is the caller's own team (`user_team` in a
slash-command payload, `user.team_id` in events; the payload `team_id` only when these are absent), not the channel's
team. The synthetic email is `slack-t0123abcd-u0456efgh@users.invalid`. `ExternalAccounts` admits the hyphen form for
SLACK only; the Discord rule and its test do not change.

**D01 — Implementation notes:**
- `domain/model/ExternalAccounts.java`: add
  `private static final Pattern SLACK_ID = Pattern.compile("[A-Za-z0-9]+-[A-Za-z0-9]+");` and pick the pattern by
  platform in `syntheticEmail` (`platform == ExternalPlatform.SLACK ? SLACK_ID : EXTERNAL_ID`); update the Javadoc
  and the exception message.
- Add `public static String slackExternalId(String teamId, String userId)`: each part must match `[A-Za-z0-9]+`, else
  `IllegalArgumentException`; returns `teamId + "-" + userId` (no case change in the stored value).
- `ExternalAccountsTest`: `slackExternalId("T0123","U0456")` -> `"T0123-U0456"`; `syntheticEmail(SLACK, "T0123-U0456")`
  -> `slack-t0123-u0456@users.invalid`; SLACK rejects `"T0123-"`, `"-U1"`, `"T1-U2-U3"`, `"T1:U2"`, `"U0456"` (no team);
  DISCORD still rejects `"12-34"`; `slackExternalId` rejects a blank or non-alphanumeric part.
- Reuse `ExternalIdentityRepository` as is; no Flyway migration; no Slack-only port.
- `CONTEXT.md`: if it defines external identity, add the Slack key shape. (The Slack adapter's use of
  `slackExternalId` is P19-03; the `docs/INDEX.md` link is P19-08.)

## Acceptance criteria
- [ ] AC1: `ExternalPlatform.SLACK` exists with label `"Slack"`; `DISCORD` is unchanged.
- [ ] AC2: `ExternalAccounts.slackExternalId("T0123", "U0456")` returns `"T0123-U0456"`; a blank, null or
  non-alphanumeric part throws `IllegalArgumentException`.
- [ ] AC3: `syntheticEmail(SLACK, "T0123-U0456")` returns `slack-t0123-u0456@users.invalid`; SLACK rejects `"T0123-"`,
  `"-U1"`, `"T1-U2-U3"`, `"T1:U2"` and `"U0456"`; DISCORD still rejects `"12-34"` and still accepts its digit ids.
- [ ] AC4: `ExternalIdentityService.resolve(SLACK, "T1-U1", name)` on first contact provisions a user with the Slack
  synthetic email, a `SLACK` link and one knowledge base named "Slack"; a second call returns the same user.
- [ ] AC5: The same Slack user id under two team ids (`T1-U1`, `T2-U1`) resolves to two different users.
- [ ] AC6: A `SLACK` link and a `DISCORD` link with the same raw id coexist in `external_identities` and each is found
  by its own platform.
- [ ] AC7: No Flyway migration is added; `dev.mindforge.domain` imports stay JDK only.

## Test plan
- AC1–AC3: unit, `src/test/java/dev/mindforge/unit/domain/ExternalAccountsTest.java` (extend; keep existing cases).
- AC4, AC5: unit with Mockito, `src/test/java/dev/mindforge/unit/application/ExternalIdentityServiceTest.java`; plus
  one SLACK case in `src/test/java/dev/mindforge/integration/application/ExternalIdentityProvisioningTest.java`
  (Testcontainers).
- AC6: integration, `src/test/java/dev/mindforge/integration/persistence/ExternalIdentityRepositoryAdapterTest.java`.
- AC7: review; `mvn -B verify -DskipFrontend=true` stays green (ArchUnit/hexagonal checks if present).

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/ExternalPlatform.java`
- `src/main/java/dev/mindforge/domain/model/ExternalAccounts.java`
- `src/test/java/dev/mindforge/unit/domain/ExternalAccountsTest.java`
- `src/test/java/dev/mindforge/unit/application/ExternalIdentityServiceTest.java`
- `src/test/java/dev/mindforge/integration/application/ExternalIdentityProvisioningTest.java`
- `src/test/java/dev/mindforge/integration/persistence/ExternalIdentityRepositoryAdapterTest.java`
- `CONTEXT.md` (only if it defines external identity)

## Out of scope
- Any `dev.mindforge.slack` code, Bolt dependencies, `AppProperties.Slack` (P19-02, P19-03).
- `UploadSource.SLACK` (lands with its producer in P19-07).
- Account linking (`ExternalIdentityRepository.link`, issue #43); `ReminderService` stays DISCORD only.
- A new ADR (ADR 0033 exists); changes to the Discord id rule.

## Notes
