---
id: Q13
status: answered
impact: low
blocks: [W02]
answered_by: docs/factory/phase-18/questions/Q08-bot-switch-and-profile.md; src/main/java/dev/mindforge/discord/DiscordConfig.java (@Profile("!cli"), BotTokenSet); docs/project/implementation-plan.md#Phase 18 (As built: "off unless DISCORD_BOT_TOKEN is set; it never runs in the cli profile")
---
## Question
When is the Slack bot built and started?

## Context
Socket Mode needs two secrets: the bot token (`xoxb-…`) and the app-level token (`xapp-…`); no signing secret and no
public HTTP endpoint. The Discord precedent: the whole adapter configuration is conditional on its token and
`@Profile("!cli")`.

## Known options
- A (settled): `SlackConfig` with `@Profile("!cli")` and a condition that both `mindforge.slack.bot-token` and
  `mindforge.slack.app-token` are set (env `SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`); one set without the other fails
  startup with a field-specific message (fail closed, like the guild list); the Socket Mode app is started by a
  lifecycle bean and stopped on close.

## Constraints
Tokens are secrets, never logged; a wiring test proves no Slack bean exists without the tokens and in the `cli` profile.
