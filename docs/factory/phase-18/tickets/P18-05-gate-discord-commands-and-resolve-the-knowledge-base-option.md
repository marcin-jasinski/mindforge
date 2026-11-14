---
id: P18-05
title: Gate every Discord slash command through the allowlist, rate limit and identity resolution, reply ephemerally, and resolve the kb option
status: ready
plan_tasks: [18.2, 18.4]
decisions: [D14, D03]
depends_on: [P18-03, P18-04]
size: L
issue: 37
commit:
fix_rounds: 0
---
## Goal
The bot listens for slash commands and `kb` autocomplete. Every command passes one gate in a fixed order: guild/role
allowlist -> per-user rate limit -> resolve-or-provision the caller -> the command. Every reply is ephemeral and goes
through one reply seam. A `kb` option is turned into an owned knowledge base id or a fixed "Unknown knowledge base."
reply. Commands plug in as `DiscordCommand` beans; the bot registers their definitions (guild context only). After this
ticket the gate is complete and tested with a fake command; P18-06..P18-08 add `/ask`, `/quiz`, `/upload`. This proves
the 18.4 test "a command from a guild not on the allowlist is rejected".

## Context
- After P18-04: `dev.mindforge.discord.DiscordConfig` (conditional on token, `@Profile("!cli")`), `DiscordBot`
  (`ApplicationRunner`, logs in), `DiscordAllowlist`, `DiscordRateLimiter`, JDA on the classpath.
- After P18-03: `ExternalIdentityService.resolve(ExternalPlatform, externalId, displayName)` -> user id (provisions the
  user and a "Discord" knowledge base on first contact).
- `application/service/KnowledgeBaseService.java`: `list(ownerId)`, `requireOwner(kbId, userId)` (throws
  `NotFoundException` / `NotOwnerException`).
- Adapter rules (`docs/standards/backend/api.md`, hexagonal.md): thin — validation + auth check + delegate; constructor
  injection; virtual threads, blocking JDA calls (`complete()`), no reactive types. User-facing texts are fixed; never
  echo exception messages or option values. Logger named `log`.
- Testability (breakdown): tests need no JDA connection. Translate a JDA event into a JDA-free input, e.g.
  `record DiscordCall(Long guildId, Set<Long> memberRoleIds, long discordUserId, String displayName, String command,
  String subcommand, Map<String, String> options, ...)` (the attachment for `/upload` is added in P18-08), and send
  replies through a small seam, e.g. `interface DiscordReply { void defer(); void send(String text); }` implemented by a
  JDA class over the slash event / interaction hook. Handlers never call JDA reply methods directly.
- Gate order (D04 and D11, applied here, both from P18-04's decisions):
  1. `allowlist.admits(guildId, memberRoleIds)`; false -> reply "This bot is not available here.", INFO log
     `Discord command rejected: guild={}` (guild id or "dm"), return. Never log the lists or the token.
  2. `rateLimiter.tryAcquire(discordUserId)` for the four model-spending commands (all commands this phase has);
     exhausted -> reply "Rate limit reached. Try again in N s." and return. Counted once per command.
  3. `reply.defer()` (ephemeral; Discord needs an acknowledgement within 3 s; provisioning and the model call follow),
     then `identity.resolve(DISCORD, String.valueOf(discordUserId), displayName)`.
  4. `command.handle(call, userId, reply)`.
  A rejected command calls only the reply seam: no identity resolution, no provisioning, no application service.
  An unexpected exception from a command -> WARN log with the exception class only, fixed reply
  "Something went wrong. Try again later." Display name passed to the resolver: global name, else username.
- Message size: Discord caps a message at 2000 chars; the reply seam splits a longer text into ordered ephemeral
  follow-ups (never truncates silently).
- ADRs: 0028 (allowlist), 0030 (kb option), 0032 (ephemeral replies).

## Decisions to apply
**D14 — Reply visibility (ADR 0032).** Decision: every Discord reply is ephemeral. This includes Query answers, quiz
questions and grades, upload results, error and rejection replies, and deferred replies (the ephemeral flag is set on the
defer, because it cannot change after the first response). There is no `public` option. Replies never carry reference
answers or grounding excerpts.
Implementation notes:
- One reply helper in the Discord adapter package owns the ephemeral flag: `deferReply(true)` /
  `reply(...).setEphemeral(true)` / follow-ups `setEphemeral(true)`; handlers do not call JDA reply methods directly.
- Error, rejection (D04) and unknown-knowledge-base (D03) replies go through the same helper.
- Tests: assert the defer/reply is ephemeral (here: the JDA reply implementation and the gate's error/rejection paths;
  each command ticket asserts its own path).

**D03 — Which knowledge base a command acts on (ADR 0030), adapter part.** Decision: `/ask`, `/quiz start` and `/upload`
each take a required `kb` string option with Discord autocomplete over the caller's own knowledge bases (name shown,
`kbId` as value). The handler parses the value as a UUID and calls `KnowledgeBaseService.requireOwner(kbId, userId)`
before any work. No stored "current knowledge base", no `/kb` command.
Implementation notes:
- Command option (JDA): `new OptionData(OptionType.STRING, "kb", "Knowledge base", true, true)` — provide one shared
  factory for P18-06..P18-08. `/quiz answer` takes no `kb`.
- Autocomplete (`CommandAutoCompleteInteractionEvent`, option `kb`): D04 allowlist first (rejected -> empty choices,
  nothing provisioned); then resolve-or-provision the caller (same service as commands); then
  `knowledgeBases.list(userId)`, filter by case-insensitive contains on the typed text, max 25,
  `Command.Choice(name truncated to 100 chars, kbId.toString())`. Autocomplete is not rate-limited (D11: only the four
  commands are counted).
- JDA-free helper, e.g. `Optional<UUID> resolveKnowledgeBase(String kbOption, UUID userId)`: `UUID.fromString`
  (IllegalArgumentException -> empty, no service call), then `requireOwner`; `NotFoundException` and
  `NotOwnerException` -> empty. On empty the command replies "Unknown knowledge base." (no existence oracle, no echo of
  the value).
- A user with zero knowledge bases: autocomplete shows no choices and any typed value fails `requireOwner`. No backstop create.
- Tests: another user's kbId -> fixed reply through the real `requireOwner` (real `KnowledgeBaseService` over a mocked
  `KnowledgeBaseRepository`), no downstream call; a non-UUID value -> fixed reply, no service call; autocomplete lists
  only the caller's knowledge bases, filters by text, caps at 25; autocomplete from a non-allowed guild returns no
  choices and provisions nothing.

## Acceptance criteria
- [ ] AC1: A command from a guild not on the allowlist gets the ephemeral reply "This bot is not available here." and
  neither the rate limiter, `ExternalIdentityService` nor any command is called (18.4).
- [ ] AC2: A command from a DM (null guild) and from a member without a required role are rejected the same way.
- [ ] AC3: A command over the rate limit gets "Rate limit reached. Try again in N s." and `ExternalIdentityService` and
  the command are not called.
- [ ] AC4: An admitted command is deferred ephemerally, the caller is resolved with `ExternalPlatform.DISCORD` and the
  snowflake as external id, and the command receives the resolved user id.
- [ ] AC5: An exception thrown by a command gives the fixed "Something went wrong. Try again later." reply; its message
  is not shown or logged.
- [ ] AC6: The JDA reply implementation calls `deferReply(true)`, sets ephemeral on every reply and follow-up, and splits
  text over 2000 chars into ordered parts of at most 2000 chars.
- [ ] AC7: `resolveKnowledgeBase` returns the id for an owned knowledge base and empty for a non-UUID value (no service
  call), an unknown id and another user's id.
- [ ] AC8: `kb` autocomplete returns only the caller's knowledge bases filtered by the typed text, at most 25, names cut
  to 100 chars; from a non-allowed guild it returns no choices and provisions nothing.
- [ ] AC9: `DiscordBot` registers the definitions of all `DiscordCommand` beans as global commands with guild-only
  interaction context, and the listener is attached to JDA; no JDA type leaves `dev.mindforge.discord`.

## Test plan
- Unit (plain Mockito, no Spring context, AssertJ), in `src/test/java/dev/mindforge/unit/discord/`:
  - `DiscordCommandHandlerTest` (new): AC1–AC5 with a fake `DiscordCommand` and a recording fake `DiscordReply`.
  - `DiscordKnowledgeBasesTest` (new) (or the chosen helper name): AC7–AC8 with the real `KnowledgeBaseService` over a
    mocked `KnowledgeBaseRepository`.
  - `JdaDiscordReplyTest` (new): AC6 with Mockito mocks of the JDA event, hook and rest actions.
- AC9: unit test of the registration/translation code where feasible with mocks; otherwise review. No test connects to Discord.

## Files likely touched
- `src/main/java/dev/mindforge/discord/DiscordCall.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordReply.java` (new) and its JDA implementation (new)
- `src/main/java/dev/mindforge/discord/DiscordCommand.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordCommandHandler.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordKnowledgeBases.java` (new; kb option factory, resolution, autocomplete)
- `src/main/java/dev/mindforge/discord/DiscordListener.java` (new; JDA event -> `DiscordCall`)
- `src/main/java/dev/mindforge/discord/DiscordBot.java`, `DiscordConfig.java`
- tests listed above (new)

## Out of scope
- The `/ask`, `/quiz`, `/upload` commands (P18-06..P18-08).
- A public-reply option, a stored current knowledge base, a `/kb` command, a DM allowlist.
- Changes to `RateLimitInterceptor` or a shared web/bot counter.

## Notes
