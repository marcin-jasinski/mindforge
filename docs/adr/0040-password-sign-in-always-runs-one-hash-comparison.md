# Password sign-in always runs one hash comparison

Status: Accepted (2026-11-16).

`AccountService.authenticate` runs exactly one `PasswordHasher.matches` call on every attempt. When the email has no
account, or the account has no password hash (an OAuth-only account), it compares the given password against a dummy
hash that the service makes once through `PasswordHasher.hash` when it is built, so the comparison costs the same as
a real one (BCrypt cost 12, in `BCryptPasswordHasher`). An unknown email, an OAuth-only account and a wrong password
all answer the same `invalidCredentials` error in about the same time. `register` keeps its `409 EMAIL_TAKEN` for a
taken email; this is an accepted risk, bounded by the auth rate limit (ADR 0038).

## Considered Options

- **Also hide a taken email in `register`** (always `201`/`202` plus email verification): closes the direct channel,
  but MindForge sends no mail, the flow is new work the Phase 20 plan does not ask for, and it changes the API
  contract (`api.generated.ts`).
- **No change beyond the rate limit; record the risk**: leaves a cheap timing oracle (~250 ms difference) that the
  code already assigned to Phase 20.
- **A hard-coded cost-12 BCrypt string in the application layer**: puts BCrypt format knowledge outside the adapter,
  and the timing drifts if the adapter's cost changes.

## Consequences

- Sign-in timing no longer tells which emails have an account or a password.
- `register` still tells a caller that an email is taken. One address is slowed by the rate limiter; enumeration from
  many addresses is not. Closing this needs mail sending and a verification flow, which is a new decision.
- Building `AccountService` costs one BCrypt hash at startup.
- Tests check that `matches` is called once in each failure case; they do not measure time.

Decided in [D04](../factory/phase-20/decisions/D04-sign-in-timing-and-enumeration.md) by the dark-factory run
`phase-20`.
