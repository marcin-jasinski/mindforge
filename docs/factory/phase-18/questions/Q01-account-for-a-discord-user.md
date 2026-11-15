---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W01, W02]
---
## Question
What MindForge account does first contact auto-provision for a Discord user, given that every `users` row needs a
unique, non-null email and the bot cannot read a Discord user's email?

## Context
Plan 18.2: "identity resolution auto-provisions on first contact"; 18.1: `createUserAndLink(...)`; 18.4: "First contact
auto-provisions". Schema: `users.email VARCHAR(255) NOT NULL` with unique index `idx_users_email`
(`src/main/resources/db/migration/V1__baseline.sql:3-14`); `password_hash` is nullable. `User` record
(`domain/model/User.java`) has `email` as a plain field; `AccountService.signInWithProvider` matches and creates by
email; `AccountService.byEmail` is how the CLI names a user (ADR 0019). Discord gives a bot only the user id (snowflake),
username and global display name; email needs the user's own OAuth2 grant with the `email` scope, which a bot
interaction does not have. The choice decides whether the account can later sign in to the web, whether the CLI can
name it (`--user=<email>`), and whether a migration changes `users`.

## Known options
- A: Synthetic, non-deliverable email, e.g. `discord-<snowflake>@users.invalid` (RFC 2606 `.invalid`), no password, no
  schema change. Account cannot sign in to the web (no password, no provider match) unless linked (Q02). The CLI can
  name it by that address. Risk: the synthetic address shows in the profile/UI as if real.
- B: Make `users.email` nullable (migration `V8`; unique index stays, Postgres allows many NULLs); `User.email` becomes
  optional; `AccountService`, `UserDtoMapper`, `OAuthLoginSuccessHandler`, JWT claims and the SPA must handle a missing
  email. Truer data, wider change.
- C: Do not provision an account: a Discord user must first link an existing web account (Q02 option with a code);
  first contact only answers "link your account". Contradicts the plan's "auto-provisions on first contact".

## Constraints
- Domain JDK only; `User` stays a record. Emails stored lowercased (`AccountService`).
- Plan 18.4 requires a test that first contact auto-provisions — option C would change the plan.
- Phase 19 reuses the same port and the same account model (plan 19 checklist).
- Display name: `TextRules.singleLine`; bounded to `VARCHAR(255)`.
- First contact can race (two commands at once): `createUserAndLink` must be one transaction, and the unique key on
  `(platform, external_id)` is the backstop.
