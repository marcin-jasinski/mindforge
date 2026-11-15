---
id: D07
question: Q07
status: decided
method: majority
class: local
votes: { pragmatist: A, architect: B, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q07.r1.tally.md]
adr: 
---
## Decision
Use A: copy the two helpers into the Slack adapter as `SlackRateLimiter` (keyed by the Slack user id `String`) and
`SlackQuizSessions` (keyed by internal user id `UUID`), with copied unit tests. Do not change any Discord class or test.
Do not import `dev.mindforge.discord` from `dev.mindforge.slack`.

## Why
- Both Discord classes are small (42 and 37 lines) and have no JDA type (DiscordRateLimiter.java, DiscordQuizSessions.java).
- Phase-18 D11 set the precedent: copy the window logic to keep tested code unchanged (D11:15,23).
- B must move or rewrite DiscordRateLimiterTest and DiscordQuizSessionsTest; the question's constraint says Discord tests must not change. The architect's claim that only DiscordConfigWiringTest exists is false.
- A keeps the two optional adapters independent and adds no new shared package (minimal implementation).
- C is out: it couples two separately switched adapters and changes the Discord key type.

## Rejected
- B: a shared generic package refactors shipped, tested Discord code and its two unit tests for about 40 lines of saved code.
- C: `dev.mindforge.slack` -> `dev.mindforge.discord` coupling; widens Discord's `tryAcquire(long)`.

## Dissent
Architect (B): the window logic now exists three times (RateLimitInterceptor, Discord, Slack); a fix to the compute()
or the retry-seconds rounding in one copy can be missed in the others. Watch: if a third chat adapter appears or the
window logic must change, extract a shared class then.

## Implementation notes
- `dev.mindforge.slack.SlackRateLimiter`: constructor `SlackRateLimiter(int limit, Duration window, Clock clock)`;
  `private record Window(long startMillis, int count)`; `Cache<String, Window>` with `expireAfterWrite(window)`;
  `OptionalLong tryAcquire(String slackUserId)` — same logic as DiscordRateLimiter.java:31-41 (retry seconds rounded
  up, minimum 1). Javadoc: say it copies the RateLimitInterceptor / DiscordRateLimiter window logic.
- `dev.mindforge.slack.SlackQuizSessions`: `public record ActiveQuiz(UUID kbId, UUID sessionId)`; constructor
  `SlackQuizSessions(Duration ttl)`; `put(UUID userId, ActiveQuiz)`, `Optional<ActiveQuiz> current(UUID userId)`,
  `clear(UUID userId)` — keyed by internal user id, not the Slack id.
- Build both beans in the Slack `@Configuration` from the existing rate-limit properties, as DiscordConfig does; no new settings.
- Tests: `src/test/java/dev/mindforge/unit/slack/SlackRateLimiterTest` and `SlackQuizSessionsTest`, copied from the
  Discord tests, with a fixed/mutable `Clock`. Cover: under limit allowed; over limit returns retry seconds >= 1;
  window reset after the window passes; separate users have separate counters; put replaces earlier quiz; clear removes it.
- No import of `dev.mindforge.discord` anywhere under `dev.mindforge.slack` (main or test).
