---
id: D04
question: Q04
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q04.r1.tally.md]
adr: docs/adr/0040-password-sign-in-always-runs-one-hash-comparison.md
---
## Decision
Use A: make `AccountService.authenticate` run exactly one `PasswordHasher.matches` call on every attempt. When the
email has no account, or the account has no password hash (OAuth-only), compare the given password against a dummy
hash that the service makes once through `hasher.hash(...)` in its constructor, so the cost is the adapter's cost
(12). All three failures throw the same `AccountException.invalidCredentials()`. Keep `register`'s `409 EMAIL_TAKEN`;
the Phase 20 auth rate limit (ADR 0038) bounds enumeration through register. Record the register channel as an
accepted risk.

## Why
- AccountService.java:42-45 skips the BCrypt compare (~250 ms at cost 12) for an unknown email and an OAuth-only
  account; the comment assigns the fix to Phase 20. The fix is local and uses the existing port.
- Making the dummy hash through the port keeps the cost the same as real hashes (BCryptPasswordHasher.java:10) and
  keeps BCrypt details out of the application layer.
- B needs an email-verification register flow; no mail sending exists, and the Phase 20 plan (roadmap.md:129-130)
  does not ask for it. It would also change the API contract.
- C leaves a cheap, confirmed timing oracle.

## Rejected
- B: needs a new register flow and mail sending that do not exist; out of Phase 20 scope; changes the API contract.
- C: leaves the timing oracle that the code assigns to Phase 20.

## Dissent
None. Risk to watch: `register`'s 409 still reveals a taken email; only the per-address rate limit slows it, and
distributed enumeration through register is not slowed.

## Implementation notes
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
- Docs: add to `docs/standards/security/web-security.md` a short "Account enumeration" note: sign-in takes the same
  time for unknown, OAuth-only and wrong-password cases; register's 409 is an accepted risk bounded by the auth rate
  limit (ADR 0038, ADR 0040).
