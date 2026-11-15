---
id: D01
question: Q01
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q01.r1.tally.md]
adr: docs/adr/0027-bot-users-provisioned-with-a-reserved-invalid-email.md
---
## Decision
Use A: on first contact, provision a `users` row with the synthetic, non-deliverable email
`<platform>-<externalId>@users.invalid` (lowercased platform, e.g. `discord-123456789012345678@users.invalid`), a null
password and a null avatar, and link it to the external identity in one transaction. Do not change the `users`
schema. `AccountService.register` and `AccountService.signInWithProvider` must refuse any email in the reserved
domain `users.invalid`. Provisioning always inserts and never resolves a user by email.

## Why
- `users.email` is NOT NULL and unique (V1__baseline.sql:6,14); `password_hash` is nullable, so A needs no migration and `User` stays unchanged.
- A Discord-only account cannot sign in to the web: `authenticate` needs a password (AccountService.java:42-44) and no provider verifies a `.invalid` address (OAuthLoginSuccessHandler email check). Linking is Q02.
- The CLI can still name the account with `--user=<email>` (ADR 0019, CliUsageRunner USAGE).
- Snowflakes are public, and `RegisterRequest` accepts any `@Email` (RegisterRequest.java:10); without the guard, a squatter locks the victim out on `idx_users_email`. One check in `AccountService` closes it ("every rule enforced in code").
- B makes `normalise` NPE on null (AccountService.java:79-81) and ripples through DTO, OAuth handler, CLI and SPA; it is the least reversible.
- C contradicts plan 18.2/18.4 (implementation-plan.md:1583,1589).
- Platform-neutral naming lets Phase 19 reuse the same rule (`slack-<id>@users.invalid`).

## Rejected
- A0 (A without the guard): a squatter can pre-register the deterministic address and deny or hijack provisioning.
- A' (random local part): loses the derivable CLI name for no gain once the guard exists.
- B (nullable email, V8): wide change, NPE surface, hard to reverse, for data no surface reads in Phase 18.
- C (no provisioning): contradicts plan 18.2/18.4.
- D (external-only principal with no `users` row): breaks KB ownership and every user-keyed port.

## Dissent
None on the choice. Risk to watch: any future path that writes `users.email` outside `AccountService` (admin import,
email-change endpoint) must apply the same reserved-domain refusal, and the synthetic address shows as the profile
email if such an account ever reaches the SPA via Q02 linking.

## Implementation notes
- Domain (JDK only), e.g. `dev.mindforge.domain.model.ExternalAccounts`:
  `public static final String RESERVED_EMAIL_DOMAIN = "users.invalid";`
  `static String syntheticEmail(String platform, String externalId)` -> `platform.toLowerCase(Locale.ROOT) + "-" + externalId.toLowerCase(Locale.ROOT) + "@users.invalid"`;
  reject an `externalId` that is blank or not `[A-Za-z0-9]+`, and keep the result <= 255 chars;
  `static boolean isReserved(String normalisedEmail)` -> `normalisedEmail.endsWith("@users.invalid")`.
- `AccountService.register` and `signInWithProvider`: after `normalise`, if `isReserved` throw `AccountException`
  (new factory, e.g. `AccountException.reservedEmail()`, mapped like the existing account errors). Unit tests for both.
- Identity resolution (application service, port shape per Q13): `findUserId(platform, externalId)`; on miss call
  `createUserAndLink(platform, externalId, new User(UUID.randomUUID(), displayName, syntheticEmail(...), null, null, null, null))`.
  Display name: global name, else username, else externalId, through `TextRules.singleLine`, truncated to 255.
- `createUserAndLink` is one `@Transactional` in the adapter (insert user + insert identity row). On a unique violation
  (`(platform, external_id)` or `idx_users_email`) the resolver re-reads `findUserId` once and returns that user.
- Tests: first contact provisions a user with the synthetic email and null password; a second contact returns the same
  user; two concurrent first contacts yield one user; `register` and `signInWithProvider` with `x@users.invalid` are refused.
