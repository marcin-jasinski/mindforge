---
id: Q08
status: answered
impact: low
blocks: [W04]
answered_by: docs/project/implementation-plan.md#Phase 14 — Observability and Tracing (14.2 "No exporter when either key is absent"); src/main/java/dev/mindforge/infrastructure/config/TracingConfig.java#LangfuseKeysSet; docs/adr/0019-cli-acts-as-a-named-user-without-password.md; src/main/java/dev/mindforge/infrastructure/config/ArticleFetchConfig.java (@Profile("!cli"))
---
## Question
When does the bot start: always, behind an explicit flag, or only when a token is configured — and in which profiles?

## Context
Plan 18.2: "JDA as a Spring-managed `ApplicationRunner`", "`AppProperties.discord.*`". A JDA login needs a bot token;
tests and the `cli` profile must not connect to Discord.

## Known options
- Settled by precedent: the bot is configured only when `DISCORD_BOT_TOKEN` (`mindforge.discord.bot-token`) is set,
  exactly like Langfuse export "off unless both keys are set" (a `Condition` on the property, as
  `TracingConfig.LangfuseKeysSet`); its `@Configuration` is `@Profile("!cli")`, as `ArticleFetchConfig`'s trigger, so a
  CLI process never connects. No separate `enabled` flag. With no token nothing Discord-related is built, and the
  application starts as today. When the token is set, allowlist settings are validated at startup (Q04). The token is
  never logged.

## Constraints
- Secrets in environment variables (`docs/standards/global/conventions.md`); listed in `deployment.md` and
  `env.example`.
- Graceful shutdown: JDA is shut down on context close (bean destroy method), within
  `spring.lifecycle.timeout-per-shutdown-phase`.
- Integration tests run without a token, so they never build JDA.
