# The auth rate limit is keyed by client address

Status: Accepted (2026-11-16).

The rate limiter on `/api/v1/auth/login` and `/api/v1/auth/register` counts every POST, rejected ones included, in an
in-process Caffeine fixed window keyed by the client address (`request.getRemoteAddr()`, made correct behind the
Railway or Render proxy by the phase-20 forwarded-header decision). A spent window answers 429 with `Retry-After` and
code `RATE_LIMITED` through the existing `RateLimitExceededException` path. The limiter does not read the request body
and does not key on the email. The R09 model-call limiter stays keyed by user and does not change.

## Considered Options

- **Per address on both endpoints plus a per-normalised-email bucket on login**: also slows a distributed guesser
  against one account, but it needs the email before the controller (a body-caching filter or a `RequestBodyAdvice`),
  a shared copy of `AccountService`'s email normalisation in the api layer, and it lets anyone lock a victim out of
  password sign-in by spending the victim's email bucket.
- **Per email on login, per address on register**: leaves credential stuffing (many accounts from one address)
  unthrottled on login, and has the same victim lock-out.

## Consequences

- Credential stuffing and mass registration from one address are capped. Distributed guessing against one account
  from many addresses is not slowed by the limiter; BCrypt cost ≥ 12 slows each attempt. A per-email bucket can be
  added later without changing this limiter.
- The limit is only as good as the client address: if the forwarded-header setup is wrong, the limit becomes one global
  bucket (all sign-ins lock together) or no limit (forged `X-Forwarded-For`).
- Users behind one NAT or proxy share one bucket; the budget must allow for that.
- State is in-process, which holds while the deployment has one live instance (ADR 0007).

Decided in [D01](../factory/phase-20/decisions/D01-auth-rate-limit-key.md) by the dark-factory run
`phase-20`.
