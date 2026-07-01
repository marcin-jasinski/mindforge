# Hexagonal Architecture Standards

MindForge follows strict Hexagonal Architecture (Ports and Adapters). These standards are non-negotiable — deviations corrupt the architecture.

## Layer Boundaries

Dependencies always point inward: adapters → application → domain. Never cross layer boundaries.

| Layer | Package | Allowed Imports |
|---|---|---|
| Domain | `dev.mindforge.domain` | JDK stdlib only; zero I/O, zero framework imports |
| Application | `dev.mindforge.application` | `dev.mindforge.domain.*` only |
| Infrastructure | `dev.mindforge.infrastructure` | `dev.mindforge.domain.*`, `dev.mindforge.application.*`, any third-party |
| Agents | `dev.mindforge.agent` | `dev.mindforge.domain.*`, `dev.mindforge.infrastructure.ai.*` |
| Adapters | `dev.mindforge.api`, `dev.mindforge.cli` | All layers (thin; no business logic) |

```java
// NEVER in dev.mindforge.domain:
import jakarta.persistence.*;       // ❌ framework import
import org.springframework.*;      // ❌ framework import
import java.net.http.HttpClient;   // ❌ I/O import

// NEVER in dev.mindforge.application:
import dev.langchain4j.*;          // ❌ LLM SDK
import org.springframework.data.*; // ❌ database driver
```

## Composition Root

Each runtime surface has **exactly one** composition root — no class-level static singletons, no static-init side effects.

Composition root:
- `MindForgeApplication.java` → Spring Boot `@SpringBootApplication` main class
- All beans are wired through Spring's `@Configuration` classes or `@Bean` methods

```java
// CORRECT: all wiring in @Configuration
@Configuration
public class ApplicationConfig {
    @Bean
    public AIGateway aiGateway(SpringAiProperties props) {
        return new SpringAIGateway(props);
    }
    // ... wire everything here
}

// NEVER: static-level singletons
public class SomeService {
    private static final DataSource DATA_SOURCE = DataSourceBuilder.create().build(); // ❌ static init
}
```

## Open/Closed Principle

Adding a new AI agent, document format parser, or auth provider means registering a new adapter — **never** modifying the orchestrator, `ParserRegistry`, or auth framework.

```java
// CORRECT: register new format
registry.register("application/epub", new EpubParser());

// NEVER: add new format by modifying PipelineService.ingest()
```

## Persistence Sub-Package Convention

`dev.mindforge.infrastructure.persistence` splits into four sub-packages — never mix types across them:

| Sub-package | Contains |
|---|---|
| `persistence.entity` | JPA `@Entity` classes |
| `persistence.jpa` | Spring Data `JpaRepository` interfaces |
| `persistence.mapper` | MapStruct entity↔domain mapper interfaces |
| `persistence.adapter` | Domain port implementations (`implements XxxRepository`), depend on `jpa` + `mapper` |

```java
// CORRECT: adapter depends on jpa repository + mapper, implements the domain port
package dev.mindforge.infrastructure.persistence.adapter;

public class DocumentRepositoryAdapter implements DocumentRepository {
    public DocumentRepositoryAdapter(DocumentJpaRepository jpaRepository, DocumentEntityMapper mapper) { ... }
}
```

## Data Store Roles

- **PostgreSQL**: Single source of truth for all business data. All writes go here first.
- **Neo4j**: Derived read projection only. Rebuilt from PostgreSQL via outbox events. Never write business data here as authoritative state.
- **Caffeine**: In-memory application cache. No distributed cache dependency; appropriate for single-instance deployment.

## Pipeline Idempotency

Every pipeline step must:
1. Check if a matching `StepFingerprint` already exists for the current inputs
2. Skip execution and return cached output if fingerprint matches
3. Checkpoint output + fingerprint to `document_artifacts` after execution
4. Publish domain event to outbox in the **same database transaction** as the checkpoint

```java
// CORRECT: checkpoint + publish in same @Transactional boundary
@Transactional
public void process(AgentContext context) {
    artifactRepository.saveCheckpoint(artifact);
    outboxRepository.publish(event);  // same transaction
}

// NEVER: publish event outside transaction
```

## Retrieval Cost Discipline

Order: **graph traversal first → full-text/lexical second → vector embeddings last**.

Always reuse the stored `referenceAnswer` from `DocumentArtifact` during quiz grading — never regenerate it.

## Agent Communication

Agents **never call each other directly**. All inter-agent data flows through the shared `DocumentArtifact` in `AgentContext`. The pipeline orchestrator (`PipelineService`) is the single coordination point.

```java
// NEVER
public class SummarizerAgent implements Agent {
    public AgentResult execute(AgentContext ctx) {
        AgentResult flashcards = flashcardAgent.execute(ctx);  // ❌ agent calling agent
    }
}

// CORRECT: orchestrator sequences agents and passes shared artifact
```

## Transactional Outbox

All domain events are propagated via the transactional outbox pattern. State changes and their events are committed in the same database transaction — guaranteeing at-least-once delivery without distributed transactions.

All event subscribers must be **idempotent** (keyed by `event_id`) because an event may be delivered more than once after relay crash recovery.
