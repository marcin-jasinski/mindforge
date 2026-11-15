---
id: D11
question: Q11
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q11.r1.tally.md]
adr:
---
## Decision
Use A: limit the bot's model-spending commands (`/ask`, `/quiz start`, `/quiz answer`, `/upload`) per Discord user in
the Discord adapter, with a small in-process Caffeine window that copies the `RateLimitInterceptor` counter logic. Use
the same settings `mindforge.rate-limits.requests` and `mindforge.rate-limits.window` (default 30 per minute), with a
counter separate from the web. Key the counter by the Discord user id (snowflake) and check it after the guild/role
allowlist (D04) and before identity resolution or provisioning (D01), so a rejected command provisions nothing and
calls no application service. An exhausted allowance replies with a fixed ephemeral (D14) message
"Rate limit reached. Try again in N s." Read-only commands stay unlimited.

## Why
- The counter is a few lines (RateLimitInterceptor.java:38-44); servlet types are only at the edges (33-35, 47-49), so a copy keeps the bot servlet-free and leaves tested web code unchanged.
- ADR 0019:28 places the per-user limit in the adapter that faces untrusted users; the bot is such an adapter.
- One operator setting (application.yml:114-117) controls both surfaces; no new property, no new dependency (Caffeine is in use).
- Keying by the external id before provisioning closes the gap where a flood of first contacts creates accounts (D01).
- C leaves model spend unbounded for any allowed member; D04 bounds who, not how much.

## Rejected
- B: refactors working web code and adds a shared class for a joint allowance; bot-provisioned users have no web session, so the gain is small (worst case 2x for a linked user).
- C: unbounded model spend per allowed-guild member (verified hard objection).

## Dissent
None on the choice. Risk to watch: a linked user (Q02) gets two allowances (web + Discord), so the bound is 2x; if a
stated per-user spend cap appears, extract a shared counter then (option B).

## Implementation notes
- Class `DiscordRateLimiter` in the Discord adapter package (package per Q09); no JDA and no servlet imports. Constructor `DiscordRateLimiter(int limit, Duration window, Clock clock)` (or `LongSupplier` for millis); `Cache<Long, Window>` with `expireAfterWrite(window)`; `private record Window(long startMillis, int count)`.
- Method `OptionalLong tryAcquire(long discordUserId)`: empty when allowed; when exhausted, the retry seconds, computed as in RateLimitInterceptor.java:43 (minimum 1).
- Build the bean in the bot's `@Configuration` from the existing rate-limits properties (as RateLimitConfig.java:28 does); no new settings.
- Order in the command handler: allowlist (D04) -> rate limit (only for the four commands) -> identity resolution/provisioning -> application service. A rejected command calls the reply helper only.
- Count `/upload` once per command, not per attachment.
- Tests (plain JUnit + Mockito, no Spring context, AssertJ): limit calls pass and call limit+1 is rejected with N s; the window resets after `window` with a fake clock; two users have separate counters; in the handler, a rejected `/ask` calls neither the identity service nor any application service and replies with the fixed ephemeral message; a read-only command is never counted.
