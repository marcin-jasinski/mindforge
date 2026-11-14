---
id: P18-04
title: Add the Discord settings, the fail-closed guild and role allowlist, the per-user command rate limit, and the JDA bot lifecycle
status: ready
plan_tasks: [18.2, 18.4]
decisions: [D04, D11]
depends_on: []
size: M
issue: 36
commit:
fix_rounds: 0
---
## Goal
With `DISCORD_BOT_TOKEN` set (and not in the `cli` profile), the application builds a JDA client, logs the bot in at
startup and shuts it down on context close. Without a token nothing Discord-related is built and the application starts
as today. Startup fails when the token is set but the guild allowlist is empty. The JDA-free gate values
`DiscordAllowlist` and `DiscordRateLimiter` exist, are tested, and are beans of the Discord configuration (the command
handler in P18-05 calls them).

## Context
- Nothing of Phase 18 exists: no JDA in `pom.xml`, no `discord` section in
  `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java` (`@Validated`, nested static classes with
  `@Valid` fields and `@AssertTrue` cross-field checks, e.g. `Articles` at ~line 91), no `dev.mindforge.discord` package.
- Package (answered question Q09, plan 18.2): `dev.mindforge.discord` is a driving adapter (may import all layers; thin;
  no business logic) with its own `@Configuration` as composition root, like `src/main/java/dev/mindforge/cli/CliConfig.java`.
  JDA types never leave `dev.mindforge.discord`.
- Switch and profile (answered question Q08): the bot is configured only when `mindforge.discord.bot-token`
  (`DISCORD_BOT_TOKEN`) is non-blank, with a `Condition` like `TracingConfig.LangfuseKeysSet`
  (`infrastructure/config/TracingConfig.java:65`); its `@Configuration` is `@Profile("!cli")` like the trigger in
  `ArticleFetchConfig`. No separate `enabled` flag. JDA is shut down on context close (bean destroy method) within
  `spring.lifecycle.timeout-per-shutdown-phase`. Integration tests run without a token, so they never build JDA. The
  token is a secret: never log it.
- `application.yml` holds settings with env placeholders (e.g. `langfuse.public-key: ${LANGFUSE_PUBLIC_KEY:}`,
  `rate-limits.requests: ${RATE_LIMIT_REQUESTS:30}`).
- Rate-limit precedent: `src/main/java/dev/mindforge/api/config/RateLimitInterceptor.java` (Caffeine window counter,
  lines ~38-44; retry seconds at ~43) built in `api/config/RateLimitConfig.java:28` from `AppProperties.RateLimits`.
- Dependency rules: minimal dependencies, recorded in `docs/project/tech-stack.md` (done in P18-10). JDA pulls OkHttp
  and Jackson 2; Spring Boot 4.1 uses Jackson 3. Exclude JDA's audio dependencies (`club.minnced:opus-java`, and
  `tink` if present and unused). Check `mvn dependency:tree` for conflicts and that
  `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` stays green.
- Never introduce reactive types: use JDA's blocking `complete()`/`awaitReady()` or callbacks.

## Decisions to apply
**D04 — Guild and role allowlist semantics (ADR 0028).** Decision: fail closed. When the bot token is set,
`mindforge.discord.allowed-guild-ids` must be non-empty, or startup fails. `allowed-role-ids` is optional and flat:
empty means any member of an allowed guild; non-empty means the member needs at least one listed role. A command from a
DM (no guild) is rejected. A command from a guild that is not on the list is rejected with a fixed reply; the bot does
not leave that guild. The check runs first in the command handler, before identity resolution, so a rejected caller
provisions nothing.
Implementation notes:
- `AppProperties.Discord` (nested, `@Valid` on the root field): `String botToken`; `Set<Long> allowedGuildIds = Set.of()`;
  `Set<Long> allowedRoleIds = Set.of()`. Binding to `Long` makes a non-numeric id a startup error.
- `@AssertTrue(message = "allowed-guild-ids must not be empty when bot-token is set") public boolean isGuildAllowlistSetWhenEnabled()`
  returns `botToken == null || botToken.isBlank() || !allowedGuildIds.isEmpty()`.
- Env vars: `MINDFORGE_DISCORD_ALLOWED_GUILD_IDS` (required with the token), `MINDFORGE_DISCORD_ALLOWED_ROLE_IDS`
  (optional), comma-separated (documented in P18-10).
- JDA-free value in `dev.mindforge.discord`: `record DiscordAllowlist(Set<Long> guildIds, Set<Long> roleIds)` with
  `boolean admits(Long guildIdOrNull, Set<Long> memberRoleIds)`: null guild -> false; guild not in set -> false;
  `roleIds` empty -> true; else `!Collections.disjoint(roleIds, memberRoleIds)`. Build it from properties in the Discord `@Configuration`.
- Slash handler order and reply text ("This bot is not available here.", INFO log `Discord command rejected: guild={}`
  with the guild id or "dm", never the lists or the token) are applied by P18-05.
- Register commands with guild-only context (`InteractionContextType.GUILD`) — done where commands are registered (P18-05).
- Tests (18.4): `DiscordAllowlistTest` — DM rejected; other guild rejected; allowed guild + empty role list admitted;
  required role missing rejected; one matching role admitted. `AppProperties` validation test: token set + empty guild
  list fails; no token + empty list passes.

**D11 — Rate limit for bot commands.** Decision: limit the bot's model-spending commands (`/ask`, `/quiz start`,
`/quiz answer`, `/upload`) per Discord user in the Discord adapter, with a small in-process Caffeine window that copies
the `RateLimitInterceptor` counter logic. Use the same settings `mindforge.rate-limits.requests` and
`mindforge.rate-limits.window` (default 30 per minute), with a counter separate from the web. Key the counter by the
Discord user id (snowflake) and check it after the guild/role allowlist (D04) and before identity resolution or
provisioning (D01). An exhausted allowance replies with a fixed ephemeral message "Rate limit reached. Try again in N s."
Read-only commands stay unlimited.
Implementation notes:
- Class `DiscordRateLimiter` in `dev.mindforge.discord`; no JDA and no servlet imports. Constructor
  `DiscordRateLimiter(int limit, Duration window, Clock clock)` (or `LongSupplier` for millis); `Cache<Long, Window>`
  with `expireAfterWrite(window)`; `private record Window(long startMillis, int count)`.
- Method `OptionalLong tryAcquire(long discordUserId)`: empty when allowed; when exhausted, the retry seconds, computed
  as in `RateLimitInterceptor.java:43` (minimum 1).
- Build the bean in the bot's `@Configuration` from the existing rate-limits properties (as `RateLimitConfig.java:28`
  does); no new settings. Leave `RateLimitInterceptor` unchanged.
- The handler order and the "count `/upload` once per command" rule are applied by P18-05/P18-08.
- Tests: limit calls pass and call limit+1 is rejected with N s; the window resets after `window` with a fake clock;
  two users have separate counters.

## Acceptance criteria
- [ ] AC1: `pom.xml` has the JDA dependency (latest stable, audio excluded); `mvn dependency:tree` shows no conflict that
  breaks Jackson 3 or OkHttp users; the full gate passes.
- [ ] AC2: `AppProperties.Discord` binds `mindforge.discord.bot-token`, `allowed-guild-ids`, `allowed-role-ids`;
  `application.yml` sets `bot-token: ${DISCORD_BOT_TOKEN:}` and the two lists with empty defaults; a non-numeric id fails binding.
- [ ] AC3: Token set and empty guild list fails validation with the message above; no token and empty list passes;
  token set and one guild passes.
- [ ] AC4: `DiscordAllowlist.admits` behaves as in D04 for all five cases.
- [ ] AC5: `DiscordRateLimiter.tryAcquire` admits `limit` calls, rejects call `limit+1` with retry seconds >= 1, resets
  after the window (fake clock), and keeps separate counters per user.
- [ ] AC6: `dev.mindforge.discord.DiscordConfig` is `@Profile("!cli")` and its beans are conditional on a non-blank
  token; with no token, no JDA, `DiscordAllowlist`, `DiscordRateLimiter` or `DiscordBot` bean exists.
- [ ] AC7: `DiscordBot` is an `ApplicationRunner` that waits for JDA to be ready and logs one INFO line without the
  token; JDA is shut down when the context closes (destroy method calls `shutdown()` and waits a bounded time).
- [ ] AC8: No class outside `dev.mindforge.discord` imports `net.dv8tion.jda`.

## Test plan
- Unit: `src/test/java/dev/mindforge/unit/discord/DiscordAllowlistTest.java` (new) for AC4;
  `src/test/java/dev/mindforge/unit/discord/DiscordRateLimiterTest.java` (new) for AC5 (fake clock).
- Unit: `src/test/java/dev/mindforge/unit/infrastructure/config/DiscordPropertiesTest.java` (new) for AC3 with a plain
  `jakarta.validation.Validator` (precedent: `ArticlePropertiesTest.java` in the same folder).
- Wiring: a reflection test in `src/test/java/dev/mindforge/discord/DiscordConfigWiringTest.java` (precedent:
  `src/test/java/dev/mindforge/infrastructure/config/ArticleFetchConfigWiringTest.java`) for the `!cli` profile, plus a
  condition test (precedent: `TracingConfigTest`, unit and integration variants) that no Discord bean exists without a
  token (AC6). The existing integration tests (no token) prove the application still starts.
- AC1, AC8: the full gate `mvn -B verify -DskipFrontend=true`; `OutboundHttpGuardTest` green; review for AC8.

## Files likely touched
- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`
- `src/main/java/dev/mindforge/discord/DiscordConfig.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordBot.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordAllowlist.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordRateLimiter.java` (new)
- tests listed above (new)

## Out of scope
- Slash command definitions, the event listener, replies, identity resolution in the bot (P18-05 onward).
- Reminder settings and the reminder job (P18-09).
- An `enabled` flag, a DM-user allowlist, leaving non-allowed guilds.
- Documentation (P18-10).

## Notes
