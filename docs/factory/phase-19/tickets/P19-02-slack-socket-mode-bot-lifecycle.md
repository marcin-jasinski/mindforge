---
id: P19-02
title: Add Bolt with the Java-WebSocket Socket Mode backend and start the Slack bot only when both tokens are set
status: done
plan_tasks: [19.2]
decisions: [D06, D16]
depends_on: []
size: M
commit: 5dbc2b7
fix_rounds: 0
issue: 46
---
## Goal
The build carries Slack Bolt and Socket Mode with the Java-WebSocket backend. `AppProperties.Slack` holds the bot
token and the app-level token (fail closed when only one is set). `dev.mindforge.slack.SlackConfig` builds a Bolt
`App` and a `SocketModeApp` (backend `JavaWebSocket`) only when both tokens are set and never in the `cli` profile;
`SlackBot` starts Socket Mode on application start and stops it on close. No command is registered yet.

## Context
- Nothing of Phase 19 exists: no `dev.mindforge.slack` package, no Slack dependency in `pom.xml`, no `Slack` section
  in `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`.
- Mirror the Discord adapter: `src/main/java/dev/mindforge/discord/DiscordConfig.java` (`@Configuration`,
  `@Profile("!cli")`, `@Conditional(BotTokenSet)` reading the property with `StringUtils.hasText`),
  `DiscordBot.java` (`ApplicationRunner`, `AutoCloseable`, lifecycle), `AppProperties.Discord` (secret token field,
  `@AssertTrue` validation), `src/main/resources/application.yml:114-122` (env mapping),
  `src/test/java/dev/mindforge/integration/discord/DiscordConfigTest.java` (`ApplicationContextRunner` wiring test),
  `src/test/java/dev/mindforge/unit/discord/DiscordBotTest.java`.
- Q13 (answered from the Phase 18 precedent): `SlackConfig` with `@Profile("!cli")` and a condition that both
  `mindforge.slack.bot-token` and `mindforge.slack.app-token` are set (env `SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`). One
  set without the other fails startup with a field-specific message (fail closed). The Socket Mode app is started by a
  lifecycle bean and stopped on close. A wiring test proves no Slack bean exists without the tokens and in `cli`.
- `pom.xml:103-109`: JDA is declared before Spring AI so JDA's `okhttp-jvm` 5 precedes Spring AI's `okhttp` 4 (both
  hold package `okhttp3`, ADR 0025). Slack's `slack-api-client` also uses OkHttp; this order must hold.
- Rules: all beans via `@Configuration`, constructor injection, no static singletons; no reactive types; secrets
  (tokens) never logged (`docs/standards/global/conventions.md`); Bolt/Slack types stay in `dev.mindforge.slack`;
  minimal dependencies documented in `docs/project/tech-stack.md`.
- ADR 0037 (`docs/adr/0037-slack-socket-mode-uses-the-java-websocket-backend.md`) is already written; cite it as
  ADR 0037 (not 0033). Do not write a new ADR.
- The `fast` command runs Maven offline (`-o`): resolve the new artifacts once online (`mvn -q dependency:resolve`)
  before using `fast`.

## Decisions to apply
**D06 — Decision:** Use B: add `com.slack.api:bolt` and `com.slack.api:bolt-socket-mode` with
`org.java-websocket:Java-WebSocket` as the WebSocket client, and select it with `SocketModeClient.Backend.JavaWebSocket`.
Do not add Tyrus, `javax.websocket-api` or `bolt-jakarta-socket-mode`.

**D06 — Implementation notes:**
- pom.xml: `com.slack.api:bolt`, `com.slack.api:bolt-socket-mode` (one `slack-bolt.version` property) and
  `org.java-websocket:Java-WebSocket` (`java-websocket.version` property), declared after JDA and before Spring AI, with a
  comment that names ADR 0037 and the okhttp ordering.
- Run `mvn dependency:tree`: no `javax.websocket-api`, no `tyrus-*`; exclude them if a Bolt module brings them. Check that
  Java-WebSocket's `slf4j-api` resolves to Boot's version, and that `okhttp-jvm` 5 still comes before `okhttp` 4.
- In `dev.mindforge.slack`, a `@Configuration` class builds `SocketModeApp` with `SocketModeClient.Backend.JavaWebSocket`;
  no Slack type leaves the package. No reactive types.
- Tests: `OutboundHttpGuardTest` stays green; a unit or wiring test asserts the backend is `JavaWebSocket`; a test (or
  documented smoke check) covers reconnect after the socket closes; the app context starts with the bot disabled
  without opening a socket.
- (Docs part — tech-stack.md row and INDEX link — is P19-08.)

**D16 — Decision:** Use A: tick "Bot connects via Socket Mode and responds to commands" on fake-driven tests. Prove
that the config builds and starts the Socket Mode app with the app-level token (fake client, no network) and that every
command handler responds (unit tests). Record a live smoke test against a real Slack workspace as a human follow-up.

**D16 — Implementation notes (code part; docs and issue part is P19-08):**
- Config test: with a fake/stub client factory, assert the Socket Mode app is built and started with the app-level token,
  and that the bot does not start when the tokens are blank. No network.
- Never log or commit `SLACK_BOT_TOKEN` / `SLACK_APP_TOKEN`; use placeholder values in tests.

## Acceptance criteria
- [x] AC1: `pom.xml` declares `bolt` and `bolt-socket-mode` (one `slack-bolt.version` property) and `Java-WebSocket`
  (`java-websocket.version`) after JDA and before Spring AI, with a comment naming ADR 0037 and the OkHttp order;
  `mvn dependency:tree` shows no `javax.websocket-api` and no `tyrus-*`, and `okhttp-jvm` 5 before `okhttp` 4.
- [x] AC2: `AppProperties.Slack` has `botToken` and `appToken` (secrets, never logged), bound from
  `mindforge.slack.bot-token` = `${SLACK_BOT_TOKEN:}` and `mindforge.slack.app-token` = `${SLACK_APP_TOKEN:}` in
  `application.yml`; validation fails with a field-specific message when exactly one of the two is set, and passes when
  both or neither are set.
- [x] AC3: Without either token, or in the `cli` profile even with both tokens, the context starts and contains no Slack
  bean (`SlackConfig` beans, `SlackBot`, Bolt `App`, `SocketModeApp`) and opens no socket.
- [x] AC4: With both tokens, `SlackConfig` builds the Socket Mode app with the app-level token and backend
  `SocketModeClient.Backend.JavaWebSocket` through a seam that a test can replace with a fake (no network).
- [x] AC5: `SlackBot` starts the Socket Mode app on application start and stops it on close; a failure to start is
  logged without any token value.
- [x] AC6: Reconnect after the socket closes is covered by a test or by a documented smoke check in Notes (Bolt's
  Socket Mode client reconnects by itself; state which).
- [x] AC7: `OutboundHttpGuardTest` stays green; no `dev.mindforge.slack` class imports `dev.mindforge.discord`.

## Test plan
- AC1: run `mvn dependency:tree` and record the result in Notes; the gate build proves the classpath.
- AC2: unit, plain Bean Validation on `AppProperties` (follow how `AppProperties.Discord` validation is tested; add a
  test class in `src/test/java/dev/mindforge/unit/slack/` or next to the existing properties test).
- AC3, AC4: integration-tagged wiring test `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`
  (`ApplicationContextRunner`, like `DiscordConfigTest`), placeholder tokens, fake Socket Mode seam.
- AC5: unit, `src/test/java/dev/mindforge/unit/slack/SlackBotTest.java` (Mockito fake of the Socket Mode app/seam).
- AC7: existing `OutboundHttpGuardTest`.

## Files likely touched
- `pom.xml`
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`
- `src/main/resources/application.yml`
- `src/main/java/dev/mindforge/slack/SlackConfig.java` (new)
- `src/main/java/dev/mindforge/slack/SlackBot.java` (new)
- `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java` (new)
- `src/test/java/dev/mindforge/unit/slack/SlackBotTest.java` (new)

## Out of scope
- `allowed-team-ids`, the allowlist, rate limiter and any command or event handler (P19-03 onward).
- Docs (`tech-stack.md`, `deployment.md`, `env.example`, `docs/INDEX.md`) — P19-08.
- Any change to the Discord adapter or its tests.
- Tyrus, `javax.websocket`, `bolt-jakarta-socket-mode`, an HTTP (Events API) endpoint or a signing secret.

## Notes
- AC1 dependency:tree (bolt 1.55.0, Java-WebSocket 1.6.0, the version Bolt 1.55.0 builds against): no `javax.websocket-api`, no `tyrus-*` (Bolt declares both `provided`, so no exclusion is needed); `okhttp-jvm` 5.5.0 (under JDA) comes before `okhttp` 4.12.0 (now under `slack-api-client`); `slf4j-api` resolves to Boot's 2.0.18.
- AC6: covered by a test, `integration/slack/SocketModeReconnectTest` (local Web API + local Java-WebSocket server; the server closes the socket and Bolt's session monitor opens a new one within its 5 s check).
- Decided: the seam is a public `SocketModeAppFactory` in `dev.mindforge.slack`, looked up with `ObjectProvider.getIfAvailable(() -> SocketModeApp::new)`, because then production defines no bean of it and a test replaces it without bean-order rules.
- Decided: `SlackBot` uses `startAsync()` and a start failure is logged at ERROR with only the exception type (no message, no stack trace) and the app keeps running, because Bolt's messages may echo request data and AC5 forbids any token value in the log.
- Decided: `AppProperties.Slack` validation uses two `@AssertTrue` checks ("app-token must be set when bot-token is set", "bot-token must be set when app-token is set"), because each names the missing field.
- Tester r1 AC map: AC1 -> Notes dependency:tree + gate build; AC2 -> unit/slack/SlackPropertiesTest (bound, neither, one-of-two both ways), integration/slack/SlackConfigTest.onlyOneTokenFailsStartup; AC3 -> SlackConfigTest (no tokens, cli profile, no socket client opened); AC4 -> SlackConfigTest (fake SocketModeAppFactory gets app token + JavaWebSocket); AC5 -> unit/slack/SlackBotTest + SlackConfigTest start/close; AC6 -> SocketModeReconnectTest; AC7 -> OutboundHttpGuardTest, grep shows no discord import in slack package.

### Deferred findings
- correctness/C1 minor 0.7 src/main/java/dev/mindforge/slack/SlackBot.java:27: A transient failure during the first Socket Mode connect leaves the bot off until the process restarts, because Bolt's auto-reconnect is only enabled after the first connect succeeds and SlackBot does not retry.
