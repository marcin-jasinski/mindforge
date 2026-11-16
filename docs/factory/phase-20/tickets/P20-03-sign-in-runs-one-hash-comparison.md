---
id: P20-03
title: Run exactly one password hash comparison on every sign-in attempt
status: in_progress
plan_tasks: [20.1]
decisions: [D04]
depends_on: []
size: S
issue: 57
commit:
fix_rounds: 0
---
## Goal
`AccountService.authenticate` calls `PasswordHasher.matches` exactly once on every attempt. For an unknown email or an
OAuth-only account (no password hash) it compares against a dummy hash made once at construction through the same
port, so sign-in takes the same time for unknown, OAuth-only and wrong-password cases. All three throw the same
`AccountException.invalidCredentials()`.

## Context
- `src/main/java/dev/mindforge/application/service/AccountService.java` lines 41–48: `authenticate` filters
  `users.findByEmail(normalise(email))` by `candidate.passwordHash() != null && hasher.matches(...)`, so an unknown
  email and an OAuth-only account skip the BCrypt compare (~250 ms at cost 12). Line 42 carries
  `// ponytail: an unknown email skips the hash comparison, so timing can reveal it; Phase 20 hardens sign-in`.
- `src/main/java/dev/mindforge/domain/port/PasswordHasher.java` (`hash`, `matches`);
  `infrastructure/security/BCryptPasswordHasher` uses cost 12.
- Application layer may import only domain, agent, Spring transaction API and SLF4J (CLAUDE.md, hexagonal.md): no
  BCrypt type in `AccountService`.
- Existing unit test: `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java` (plain Mockito; `make*`
  factories).
- ADR 0040 (`docs/adr/0040-password-sign-in-always-runs-one-hash-comparison.md`) is already written; do not write a
  new ADR.

## Decisions to apply
**D04 — Decision:** Use A: make `AccountService.authenticate` run exactly one `PasswordHasher.matches` call on every
attempt. When the email has no account, or the account has no password hash (OAuth-only), compare the given password
against a dummy hash that the service makes once through `hasher.hash(...)` in its constructor, so the cost is the
adapter's cost (12). All three failures throw the same `AccountException.invalidCredentials()`. Keep `register`'s
`409 EMAIL_TAKEN`; the Phase 20 auth rate limit (ADR 0038) bounds enumeration through register. Record the register
channel as an accepted risk.

**D04 — Implementation notes:**
- `AccountService`: add `private final String dummyHash;` set in the constructor as
  `hasher.hash(UUID.randomUUID().toString())` (one BCrypt hash at bean creation, about 250 ms; acceptable).
- `authenticate(email, password)`: `Optional<User> found = users.findByEmail(normalise(email));`
  `String hash = found.map(User::passwordHash).orElse(null);`
  `boolean ok = hasher.matches(password, hash != null ? hash : dummyHash);`
  throw `AccountException.invalidCredentials()` unless `found.isPresent() && hash != null && ok`; then `recordLogin`
  as today. Never call `matches` twice and never return before it.
- Remove the ponytail comment at line 42. No change to `AuthController`, `GlobalExceptionHandler`, the OpenAPI
  contract or `api.generated.ts`.
- Unit tests in `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java` (plain Mockito, AssertJ, no
  timing assertions): for unknown email, OAuth-only account (`passwordHash == null`) and wrong password, assert
  `invalidCredentials` and `verify(hasher, times(1)).matches(eq(password), anyString())`; for the unknown and
  OAuth-only cases, assert the hash passed is the one `hasher.hash` returned at construction; assert `recordLogin` is
  never called on failure; keep the success case. Stub `hasher.hash(anyString())` to return a non-null value in
  `makeHasher()` if it does not already.
- (The "Account enumeration" note in `web-security.md` is P20-08.)

## Acceptance criteria
- [ ] AC1: For an unknown email, an OAuth-only account and a wrong password, `authenticate` throws
  `AccountException.invalidCredentials()` (same code and message) and calls `hasher.matches` exactly once.
- [ ] AC2: For the unknown-email and OAuth-only cases, the hash passed to `matches` is the value `hasher.hash` returned
  when the service was constructed.
- [ ] AC3: `recordLogin` is never called on a failed attempt; a correct password still signs in and records the login.
- [ ] AC4: The ponytail comment is gone; `AccountService` imports no BCrypt or Spring Security type; no change to
  `AuthController`, the error handler or the OpenAPI contract.
- [ ] AC5: Every other construction of `AccountService` (tests, configuration) still works with a hasher whose `hash`
  returns a value.

## Test plan
- AC1–AC3: unit, plain Mockito, `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java` (extend).
- AC4, AC5: review; `ApiFlowTest.registerSetsAnHttpOnlyCookieThatSignsInUntilLogout` and the full gate
  `mvn -B verify -DskipFrontend=true` stay green.

## Files likely touched
- `src/main/java/dev/mindforge/application/service/AccountService.java`
- `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java`

## Out of scope
- Any change to `register` (its `409 EMAIL_TAKEN` stays), email verification, account lockout, MFA.
- Timing assertions in tests.
- Docs (`web-security.md` note) — P20-08. ADR 0040 exists.

## Notes
- Decided: `makeHasher()` in AccountServiceTest is now a Mockito mock with the same "hashed:" answers (was an anonymous class) because the tests must verify `matches` call counts and stub the construction hash.
- Test r1 AC1: AccountServiceTest shouldCompareAnUnknownEmailOnce..., shouldCompareAnAccountWithoutAPasswordOnce..., shouldCompareAWrongPasswordOnceAgainstTheStoredHash (times(1) + same message).
- Test r1 AC2: the unknown-email and no-password tests verify matches("guess","dummy-hash").
- Test r1 AC3: same three tests verify recordLogin never; existing success test verifies recordLogin.
- Test r1 AC4/AC5: diff review (comment gone, no BCrypt import; AccountConfig is the only other construction, uses real hasher); gate green except baseline.
