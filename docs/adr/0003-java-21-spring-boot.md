# Java 21 + Spring Boot 3.2 as the application platform

MindForge uses Java 21 LTS and Spring Boot 3.2+ as its backend application platform.
The primary constraint driving this choice is developer reviewability: every line of code
must be understandable and auditable by the sole developer. Java 21 LTS provides virtual
threads (eliminating the need for a reactive programming model), records and sealed
interfaces for immutable domain objects, and a mature, stable ecosystem with long-term
support. Spring Boot 3.2 pairs auto-configuration and production-ready defaults with
direct support for virtual threads via a single configuration flag
(`spring.threads.virtual.enabled=true`).

## Considered Options

- **Kotlin + Spring Boot** — rejected: adds a second language to the codebase without
  meaningful ergonomic benefit over Java 21 records and sealed interfaces; introduces
  coroutine concurrency model on top of virtual threads, which conflicts rather than
  combines cleanly.

- **Node.js + NestJS** — rejected: JavaScript/TypeScript on the backend would require
  maintaining two runtimes (Node for backend, JVM-based tooling for the AI pipeline).
  No strong type system at the domain boundary and limited compile-time safety.

- **Go** — rejected: no mature LLM integration ecosystem comparable to Spring AI;
  hexagonal architecture is conventional in Java/Spring and has first-class tooling support.

## Consequences

- Java 21 LTS receives security patches until September 2029 (Oracle) and 2031 (Adoptium
  free LTS). No mandatory platform upgrades during the project's anticipated active
  development period.
- Virtual threads are enabled globally; blocking code (DB queries, LLM calls, file I/O)
  runs on virtual threads without manual `CompletableFuture` chaining.
- Key Java 21 features used throughout: `record` types for domain objects, `sealed`
  interfaces for discriminated unions (e.g., `AgentResult`), pattern matching for `switch`
  in dispatch logic, text blocks for SQL and prompt templates.
- Maven 3.9+ is the build tool; `frontend-maven-plugin` integrates the Angular build into
  the Maven lifecycle so `mvn package` produces a single deployable JAR.
