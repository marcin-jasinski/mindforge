---
id: D06
question: Q06
status: decided
method: unanimous
class: adr
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q06.r1.tally.md]
adr: docs/adr/0037-slack-socket-mode-uses-the-java-websocket-backend.md
---
## Decision
Use B: add `com.slack.api:bolt` and `com.slack.api:bolt-socket-mode` with `org.java-websocket:Java-WebSocket` as the
WebSocket client, and select it with `SocketModeClient.Backend.JavaWebSocket`. Do not add Tyrus, `javax.websocket-api`
or `bolt-jakarta-socket-mode`.

## Why
- All three lenses chose B; no hard objection was raised against any option.
- pom.xml has no WebSocket, Tyrus or Slack dependency today (grep confirmed), so every option adds a new stack; B adds
  the fewest jars (tech-stack.md asks for minimal dependencies).
- B adds no `javax`/`jakarta` WebSocket API, so there is no namespace split (A) and no second Jakarta WebSocket API next
  to the embedded server (C).
- B does not touch OkHttp; the `okhttp-jvm` 5 ordering rule (pom.xml:103-109, ADR 0025:22-23) holds if Bolt is declared
  after JDA.
- Load-bearing (new runtime dependency), so ADR 0037 records it.

## Rejected
- A: brings the retired `javax.websocket` API and a container-style client into a Jakarta Boot 4.1 application.
- C: namespace-correct but adds several Tyrus 2.x modules and a second `jakarta.websocket` API for a client-only need.

## Dissent
None. Risk to watch (all lenses): Java-WebSocket has less field use with Bolt than Tyrus; reconnect or ping instability
would reopen this in favour of C.

## Implementation notes
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
- docs/project/tech-stack.md "Key Dependencies Summary": one row for `bolt` + `bolt-socket-mode` + `Java-WebSocket`
  (purpose, package `dev.mindforge.slack`, ADR 0037). docs/INDEX.md ADR paragraph: add 0037.
