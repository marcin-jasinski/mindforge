---
id: Q13
status: decided
decision: D13
impact: medium
blocks: [W01, W02]
---
## Question
Which mock HTTP server does `ArticleFetcherTest` use, and how does a test reach it on loopback when `EgressPolicy`
must reject loopback?

## Context
- Plan 17.3: "`ArticleFetcherTest` with a mock HTTP server, including a policy violation."
- A local server listens on `127.0.0.1`, which the policy rejects (17.1). The test therefore needs a seam that does
  not weaken production.
- On the classpath: JDK `com.sun.net.httpserver.HttpServer` (no dependency). WireMock / OkHttp MockWebServer would be
  new test dependencies.

## Known options
- A: JDK `HttpServer`; the policy takes an injected address resolver (a function host → addresses) so the test maps a
  public-looking name (`articles.test`) to the server's loopback address only for the connect step, while the check
  sees a public address. Production wiring uses the real resolver.
- B: JDK `HttpServer`; the policy has a constructor option "allow loopback" set only by tests (never by
  `AppProperties`). Simple; the policy-violation test uses a policy without the option.
- C: WireMock test dependency, plus A or B.

## Constraints
- Unit tests load no Spring context; AssertJ (`test-writing.md`).
- No production setting may disable private-range rejection unless Q02/Q03 decide so.
- Minimal dependencies.
