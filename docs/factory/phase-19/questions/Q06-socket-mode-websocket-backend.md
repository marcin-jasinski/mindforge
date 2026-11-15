---
id: Q06
status: decided
decision: D06
impact: medium
blocks: [W02]
---
## Question
Which Bolt Socket Mode module and WebSocket backend does the project add?

## Context
- Plan: "Slack Bolt for Java", "Socket Mode". Bolt needs `com.slack.api:bolt` plus a Socket Mode module and a
  WebSocket client: `bolt-socket-mode` with Tyrus 1.x (`javax.websocket-api` + `tyrus-standalone-client`, Bolt's
  default), `bolt-socket-mode` with `SocketModeClient.Backend.JavaWebSocket` (`org.java-websocket:Java-WebSocket`), or
  `bolt-jakarta-socket-mode` with a Jakarta WebSocket client (Tyrus 2.x).
- Spring Boot 4.1 is on the Jakarta namespace. The project already carries two OkHttp lines (JDA's `okhttp-jvm` 5 must
  precede Spring AI's `okhttp` 4, `pom.xml:103-109`, ADR 0025); `slack-api-client` uses OkHttp too and Gson.
- `tech-stack.md` asks for minimal, documented dependencies.

## Known options
- A: `bolt-socket-mode` + Tyrus 1.x standalone client (javax), Bolt's default path.
- B: `bolt-socket-mode` + Java-WebSocket backend (small, no javax/jakarta API jar).
- C: `bolt-jakarta-socket-mode` + Tyrus 2.x Jakarta client (namespace matches Boot).

## Constraints
- Keep `okhttp-jvm` 5 first on the classpath; `OutboundHttpGuardTest` stays green; check the dependency tree for
  conflicts with Boot-managed versions (Jackson 3, Jakarta).
- Document the choice in `tech-stack.md` "Key Dependencies Summary"; Slack types stay in `dev.mindforge.slack`.
- No reactive types (CLAUDE.md).
