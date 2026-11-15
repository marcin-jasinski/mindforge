# Slack Socket Mode uses Bolt's Java-WebSocket backend

Status: Accepted (2026-11-15).

The Slack bot connects through Socket Mode with `com.slack.api:bolt` and `com.slack.api:bolt-socket-mode`, and the
WebSocket client is `org.java-websocket:Java-WebSocket` (version pinned by a property), selected with
`SocketModeClient.Backend.JavaWebSocket` in the Slack configuration class. It is one small jar with no `javax.websocket`
or `jakarta.websocket` API and no service-loader discovery, so it does not touch the Jakarta namespace that Spring Boot
4.1 uses or the embedded server's WebSocket classes. Slack types stay in `dev.mindforge.slack`. The Bolt dependencies are
declared after JDA, so JDA's `okhttp-jvm` 5 stays before the `okhttp` 4 that `slack-api-client` and Spring AI bring
(both hold package `okhttp3`, ADR 0025).

## Considered Options

- **`bolt-socket-mode` with Tyrus 1.x (`javax.websocket-api` + `tyrus-standalone-client`)**: Bolt's default, with the
  most field use, but it adds the retired `javax` WebSocket API and a container-style client to a Jakarta application.
- **`bolt-jakarta-socket-mode` with a Tyrus 2.x Jakarta client**: the namespace matches Boot, but it adds a second
  `jakarta.websocket` API and several Tyrus modules for a client-only need, with a possible clash with the embedded
  server's classes that shows only at runtime.

## Consequences

- `mvn dependency:tree` must show no `javax.websocket`, `jakarta.websocket` (other than Boot's own) or Tyrus jar from
  Bolt; exclude any that a Bolt module brings transitively. `OutboundHttpGuardTest` stays green and `okhttp-jvm` 5 stays
  first.
- Java-WebSocket has less field use with Bolt than Tyrus. A test covers reconnect after the socket closes.
- The backend is one enum value and one jar. A move to Tyrus 2.x is a `pom.xml` change and a one-line change, and is a
  new decision.
- `tech-stack.md` "Key Dependencies Summary" lists the Bolt artifacts and Java-WebSocket.

Decided in [D06](../factory/phase-19/decisions/D06-socket-mode-websocket-backend.md) by the dark-factory run
`phase-19`.
