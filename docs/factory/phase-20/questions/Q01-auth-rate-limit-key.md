---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W02]
---
## Question
What does the auth rate limiter count against: the client address, the submitted email, or both (two buckets, a
request refused when either is spent)?

## Context
- Plan 20.3: "Caffeine-backed limiter on `/api/auth/login` and `/api/auth/register`; 429 with `Retry-After`." No key
  is named.
- The existing limiter (`api/config/RateLimitInterceptor.java`, R09) is keyed by `CurrentUser.userId()` and returns
  early for anonymous requests. Login and register are anonymous (`SecurityConfig` `permitAll`), so it never fires on
  them; a new key is needed.
- Threats: online password guessing against one account (from one or many addresses), credential stuffing (many
  accounts from one address), and mass registration (each new account gets its own model-call allowance under R09).
- Per-address only: a distributed guesser against one account is not slowed; and everyone behind one NAT or proxy
  shares one bucket (see Q02). Per-email only: an attacker can lock a victim out of password sign-in by spending the
  victim's bucket, and it does nothing for register spam with fresh emails. Both: more state, two counters.
- The email must be read from the JSON body before the controller; a servlet `HandlerInterceptor` sees the body only
  if it is cached. A per-address key needs no body.

## Known options
- A: Per client address only, one bucket for login + register (or two, see Q03).
- B: Per client address for both endpoints, plus a per-normalised-email bucket on login only.
- C: Per normalised email on login, per address on register.

## Constraints
- Caffeine, in-process (plan 20.3; ADR 0007; `deployment.md` § One live instance). No new library
  (`minimal-implementation.md`, `conventions.md` § minimal dependencies).
- `429` with `Retry-After` and code `RATE_LIMITED` through `GlobalExceptionHandler` (`api.md` § Rate Limit Headers).
- Thin controllers: the check is an interceptor or filter, not controller logic (CLAUDE.md § API/Controllers).
- Never log the password; an email in a cache key must be normalised the same way `AccountService` does.
- R09 precedent: rejected requests count (the limiter caps attempts, not successes).
