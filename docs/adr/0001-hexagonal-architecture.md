# Hexagonal architecture over standard layered architecture

MindForge uses hexagonal architecture (Ports and Adapters) rather than the conventional
Spring Boot layered pattern (Controller → Service → Repository). The domain layer is pure
Java with no Spring annotations, no JPA imports, and no framework dependencies. Application
services orchestrate through port interfaces. Infrastructure adapters implement those ports
and are wired at the single composition root. This makes every use case independently
testable without starting a Spring context, and it makes the cost of swapping a database,
an LLM provider, or a storage backend a matter of writing one new adapter class.

## Considered Options

- **Standard layered (Controller → Service → Repository)** — rejected: at MindForge's
  complexity (9 AI agents, 3 data stores, multiple delivery channels, async pipeline),
  business logic consistently leaks from services into controllers and repositories in
  the layered pattern. Hexagonal enforces the boundary at compile time via Java interfaces.

## Consequences

- `@Entity` classes live exclusively in `infrastructure/persistence/`; domain uses Java
  records. Mappers translate between them at the infrastructure boundary.
- Unit tests for application use cases mock port interfaces and run without a Spring context
  — fast, deterministic, no Testcontainers needed.
- Adding a new AI agent, parser, or delivery channel means registering a new adapter;
  the orchestrator and domain layer are never modified.
