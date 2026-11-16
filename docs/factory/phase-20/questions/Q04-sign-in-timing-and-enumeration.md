---
id: Q04
status: decided
decision: D04
impact: medium
blocks: [W03]
---
## Question
How far does Phase 20 harden password sign-in against account discovery: make `authenticate` take the same time for
an unknown email, an OAuth-only account and a wrong password, and does it also change `register`'s `409` for a taken
email?

## Context
- `application/service/AccountService.java:42`: `// ponytail: an unknown email skips the hash comparison, so timing
  can reveal it; Phase 20 hardens sign-in`. An unknown email and an account with `passwordHash == null` (OAuth-only)
  both return without a BCrypt compare (~250 ms at cost 12), so response time reveals which emails exist.
- `register` answers `409` "The email already has an account" (`AuthController`), which reveals the same fact
  directly; fixing only timing leaves this channel open. Hiding it needs a different register flow (always `201`/`202`
  plus email verification), which MindForge does not have (no mail sending).
- `web-security.md` does not name enumeration; the code comment assigns the timing fix to Phase 20.

## Known options
- A: Timing only: compare against a fixed dummy BCrypt hash (cost 12) when there is no account or no password hash;
  `register` keeps `409`; the rate limit (Q01–Q03) bounds enumeration through register.
- B: A, and change `register` to not reveal a taken email (needs a new flow; out of plan scope).
- C: Nothing beyond the rate limit; remove the comment and record the accepted risk.

## Constraints
- BCrypt cost ≥ 12 through the `PasswordHasher` port (`web-security.md` § Password Hashing; `BCryptPasswordHasher`).
- Application layer imports only domain/agent + Spring transaction + SLF4J (hexagonal.md).
- The API contract (`api.generated.ts` in the frontend) changes if `register`'s status changes.
- `minimal-implementation.md`: no new flows the plan does not ask for.
