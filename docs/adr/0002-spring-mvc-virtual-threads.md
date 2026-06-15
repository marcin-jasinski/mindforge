# Use Spring MVC with virtual threads instead of Spring WebFlux

We chose Spring MVC with Java 21 virtual threads (`spring.threads.virtual.enabled=true`)
over Spring WebFlux (Project Reactor) for all HTTP handling and background pipeline
execution. The AI pipeline involves long-running, blocking I/O — LLM calls, database
writes, file parsing — where backpressure (the primary advantage of reactive streams)
provides no benefit. Virtual threads yield OS threads on I/O automatically, giving
equivalent throughput to WebFlux while keeping code as straight-line, blocking Java that
is easy to read, debug, and reason about.

## Considered Options

- **Spring WebFlux (Project Reactor)** — rejected: Mono/Flux operator chains significantly
  reduce code readability. Stack traces span reactor internals, making debugging painful.
  No throughput advantage exists at personal-tool scale, and the mental model adds
  complexity without a corresponding benefit for this workload.

## Consequences

- Background pipeline execution uses `@Async` with a virtual thread executor. Progress
  updates use Spring's `SseEmitter` for server-sent events.
- If future requirements demand true streaming backpressure (e.g., streaming LLM token
  responses at scale to many concurrent users), Spring WebFlux can be introduced for
  specific endpoints without rewriting the full application.
