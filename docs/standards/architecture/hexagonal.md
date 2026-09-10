# Hexagonal Architecture Standards

MindForge follows strict Hexagonal Architecture (Ports and Adapters). These standards are non-negotiable — deviations corrupt the architecture.

## Layer Boundaries

Dependencies always point inward: adapters → application → domain. Never cross layer boundaries.

| Layer | Package | Allowed Imports |
|---|---|---|
| Domain | `dev.mindforge.domain` | JDK stdlib only; zero I/O, zero framework imports |
| Application | `dev.mindforge.application` | `dev.mindforge.domain.*` only |
| Infrastructure | `dev.mindforge.infrastructure` | `dev.mindforge.domain.*`, `dev.mindforge.application.*`, any third-party |
| Model services | `dev.mindforge.agent` | `dev.mindforge.domain.*`, `dev.mindforge.infrastructure.ai.*` |
| Adapters | `dev.mindforge.api`, `dev.mindforge.cli` | All layers (thin; no business logic) |

`dev.mindforge.agent` holds the concrete services that call a model (`ClaimExtractor`, `PageWriter`, …). There is no `Agent` interface (ADR 0013).

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
public class PersistenceConfig {
    @Bean
    WikiStore wikiStore(WikiPageJpaRepository pages, WikiPageEntityMapper mapper) {
        return new WikiStoreAdapter(pages, mapper);
    }
}

// NEVER: static-level singletons
public class SomeService {
    private static final DataSource DATA_SOURCE = DataSourceBuilder.create().build(); // ❌ static init
}
```

## Open/Closed Principle — where it applies

Adding a new **document format parser** or **auth provider** means registering a new adapter — **never** modifying `ParserRegistry` or the auth framework. These are genuinely open: new formats arrive from outside the design.

```java
// CORRECT: register new format
registry.register("application/epub", new EpubParser());

// NEVER: add new format by modifying IngestionService
```

The **ingest pipeline is deliberately closed**: a fixed sequence of concrete services in a known order (ADR 0013). A new step is a design change to `IngestPipeline`, not a plugin.

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

## Tenancy Is Structural

- Every `WikiStore` method takes `kbId` as its **first** argument, and every adapter query binds it.
- Every wiki, history and study table carries `knowledge_base_id`.
- Ownership ("does this user own this knowledge base?") is checked once per request in the controller or application service. The store **scopes**; it never authorizes.

```java
// CORRECT
Optional<WikiPage> findByPath(UUID kbId, String path);

// NEVER: a page lookup that can cross knowledge bases
Optional<WikiPage> findByPath(String path);  // ❌
```

## Data Store Roles

- **PostgreSQL**: The only data store. All business data, the wiki, its full history and all run records.
- **Caffeine**: In-memory application cache (quiz sessions). No distributed cache dependency; appropriate for single-instance deployment.

There is no graph database and no vector store (ADR 0016).

## Ingest Runs and Idempotency

Nothing is skipped on a re-run — a re-run is a new run against the current wiki (ADR 0015). Every page-writing operation (ingest, conversation edit, revert, Lint):

1. Claims the knowledge base's lease (`active_run_id`) or stays queued
2. Generates everything that needs a model **outside** any transaction
3. Commits pages, revisions, sources, links, the run's status and its domain events in **one** `@Transactional` boundary
4. Counts what it wrote from the rows it inserted — never from a model's report
5. Releases the lease in its final transaction

```java
// CORRECT: generated bodies land with the run record and events in one transaction
@Transactional
public void commitWrites(UUID kbId, IngestRun run, List<PageWrite> writes) {
    writes.forEach(w -> wikiStore.savePage(kbId, w));
    ingestRunRepository.markWritten(kbId, run.runId(), failures);
    eventPublisher.publish(new IngestStepCompleted(run.runId(), "write", clock.instant()));
}

// NEVER: call a model inside the commit transaction (holds locks across minutes of LLM calls)
```

## Retrieval Cost Discipline

Order: **the rendered index first → a lexical prefilter only past 20K tokens of index → no vector store** (ADR 0016).

Deterministic code before a SMALL model; a SMALL model before a LARGE one.

Quiz grading reuses the reference answer stored in the quiz session — never regenerate it.

## Model Service Communication

Model services **never call each other**. The application service that owns the use case (`IngestPipeline`, `QueryService`, `LintService`, …) sequences them and passes typed values between them.

```java
// NEVER
public class PageWriter {
    String write(PageWriteTask task) {
        linkChecker.check(...);  // ❌ model service calling model service
    }
}

// CORRECT: the pipeline sequences them
String body = pageWriter.write(task, existingBody, index);
List<LinkInsertion> links = linkChecker.check(bodies, index);
```

## Domain Events

There is no outbox table (ADR 0015). State changes and their domain events are published in the **same** `@Transactional` boundary through `EventPublisher`; listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)`.

Every consumer is derived or best-effort (SSE progress, cache eviction, waking the ingest worker), so listeners **must tolerate missing an event** — a crash between commit and listener is repaired by the startup sweep or the next read, never by redelivery.
