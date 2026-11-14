---
id: P18-02
title: Reserve the users.invalid email domain for bot accounts and refuse it on registration and provider sign-in
status: ready
plan_tasks: [18.1]
decisions: [D01]
depends_on: [P18-01]
size: S
issue: 34
commit:
fix_rounds: 0
---
## Goal
The domain can derive the synthetic email of a bot-provisioned account (`discord-<id>@users.invalid`), and no person can
register or sign in with a provider using an address in the reserved domain `users.invalid`. This closes the squatting
path before provisioning exists.

## Context
- `src/main/java/dev/mindforge/application/service/AccountService.java`: `register(displayName, email, password)` and
  `signInWithProvider(email, displayName, avatarUrl)`; both `normalise` the email (lines ~79-81).
- `src/main/java/dev/mindforge/domain/model/AccountException.java` (extends `SecurityException`, factories
  `emailTaken()`, `invalidCredentials()`, `conflict()`), mapped in `api/config/GlobalExceptionHandler.java`.
- `ExternalPlatform` enum exists after P18-01.
- ADR `docs/adr/0027-bot-users-provisioned-with-a-reserved-invalid-email.md` records the account model.
- Unit tests: `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java` (plain Mockito); domain tests in
  `src/test/java/dev/mindforge/unit/domain/`.

## Decisions to apply
**D01 — Account for a Discord user (ADR 0027).** Decision: on first contact, provision a `users` row with the synthetic,
non-deliverable email `<platform>-<externalId>@users.invalid` (lowercased platform, e.g.
`discord-123456789012345678@users.invalid`), a null password and a null avatar, and link it to the external identity in
one transaction. Do not change the `users` schema. `AccountService.register` and `AccountService.signInWithProvider`
must refuse any email in the reserved domain `users.invalid`. Provisioning always inserts and never resolves a user by email.
Implementation notes (this ticket's part):
- Domain (JDK only), e.g. `dev.mindforge.domain.model.ExternalAccounts`:
  `public static final String RESERVED_EMAIL_DOMAIN = "users.invalid";`
  `static String syntheticEmail(ExternalPlatform platform, String externalId)` ->
  `platform.name().toLowerCase(Locale.ROOT) + "-" + externalId.toLowerCase(Locale.ROOT) + "@users.invalid"`;
  reject an `externalId` that is blank or not `[A-Za-z0-9]+`, and keep the result <= 255 chars;
  `static boolean isReserved(String normalisedEmail)` -> `normalisedEmail.endsWith("@users.invalid")`.
- `AccountService.register` and `signInWithProvider`: after `normalise`, if `isReserved` throw `AccountException`
  (new factory, e.g. `AccountException.reservedEmail()`, mapped like the existing account errors). Unit tests for both.
- Risk noted in D01: any future path that writes `users.email` outside `AccountService` must apply the same refusal.

## Acceptance criteria
- [ ] AC1: `ExternalAccounts.syntheticEmail(DISCORD, "123456789012345678")` returns
  `discord-123456789012345678@users.invalid`.
- [ ] AC2: `syntheticEmail` rejects a blank id and an id with characters outside `[A-Za-z0-9]` with a domain exception
  (not bare `RuntimeException`), and never returns more than 255 chars.
- [ ] AC3: `isReserved` is true for `x@users.invalid` and false for `x@users.invalid.com` and `x@example.com`.
- [ ] AC4: `AccountService.register` with `X@Users.Invalid` (any case) throws `AccountException` and inserts nothing.
- [ ] AC5: `AccountService.signInWithProvider` with an address in `users.invalid` throws `AccountException` and inserts nothing.
- [ ] AC6: The web maps the new refusal like the other account errors (no stack trace or raw message leaks); an existing
  registration test with a normal email still passes.

## Test plan
- Unit: `src/test/java/dev/mindforge/unit/domain/ExternalAccountsTest.java` (new) for AC1–AC3.
- Unit: `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java` for AC4–AC5 (Mockito `UserRepository`,
  verify no `insert`).
- AC6: covered by the existing `GlobalExceptionHandler` mapping of `AccountException`; add an assertion only if the new
  factory needs a new status/code.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/ExternalAccounts.java` (new)
- `src/main/java/dev/mindforge/domain/model/AccountException.java`
- `src/main/java/dev/mindforge/application/service/AccountService.java`
- `src/main/java/dev/mindforge/api/config/GlobalExceptionHandler.java` (only if needed)
- `src/test/java/dev/mindforge/unit/domain/ExternalAccountsTest.java` (new)
- `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java`

## Out of scope
- Provisioning itself (P18-03). Nullable email, a `users` migration, a random local part.
- Frontend changes.

## Notes
