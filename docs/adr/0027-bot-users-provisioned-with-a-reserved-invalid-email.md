# A bot user is provisioned with a reserved `.invalid` email

Status: Accepted (2026-11-14).

On first contact from a chat platform (Discord in Phase 18, Slack in Phase 19), MindForge creates a `users` row with
the synthetic address `<platform>-<externalId>@users.invalid`, a null password and a null avatar, and links it to the
external identity in the same transaction. The `users` schema does not change: `email` stays NOT NULL and unique.
The domain `users.invalid` is reserved: `AccountService.register` and `AccountService.signInWithProvider` refuse any
email in it, and provisioning always inserts and never resolves a user by email. A bot-provisioned account cannot
sign in to the web, because it has no password and no identity provider verifies a `.invalid` address. The CLI can
name it by its synthetic address (ADR 0019).

## Considered Options

- **Make `users.email` nullable (migration V8)**: truer data, but `User.email`, `AccountService.normalise`, the user
  DTO, the OAuth success handler, the CLI `--user=<email>` and the SPA must all handle a missing email. A nullable
  column is hard to reverse once NULL rows exist.
- **No provisioning; the user must link a web account first**: contradicts plan 18.2 and 18.4 ("first contact
  auto-provisions").
- **Synthetic address without a reserved-domain guard**: the external id is public, so anyone could register the
  address first and block (or, with a find-by-email resolver, take over) the victim's account.
- **Random local part**: gives no extra safety once the domain is reserved, and the CLI could no longer derive the
  address from the external id.

## Consequences

- No migration. A later migration can replace synthetic addresses with one `WHERE email LIKE '%@users.invalid'`.
- Any new code path that writes `users.email` must refuse the reserved domain.
- A synthetic address can show as a profile email if a bot account reaches the web through account linking (Q02).
- Phase 19 reuses the same rule with its own platform prefix.

Decided in [D01](../factory/phase-18/decisions/D01-account-for-a-discord-user.md) by the dark-factory run
`phase-18`.
