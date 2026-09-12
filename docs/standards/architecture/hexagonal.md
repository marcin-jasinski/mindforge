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
| `persistence.adapter` | Domain port and query-port implementations (`implements XxxRepository`, `implements RunReportQuery`), depend on `jpa` + `mapper` |

```java
// CORRECT: adapter depends on jpa repository + mapper, implements the domain port
package dev.mindforge.infrastructure.persistence.adapter;

public class DocumentRepositoryAdapter implements DocumentRepository {
    public DocumentRepositoryAdapter(DocumentJpaRepository jpaRepository, DocumentEntityMapper mapper) { ... }
}
```

Query ports (`RunReportQuery`, `WikiHealthQuery`, `BundleQuery`) are read-only and return domain records or projections, never entity types. `WikiStore` stays page-shaped; a read that joins across tables for one consumer belongs on that consumer's query port.

## Tenancy Is Structural

- Every tenant-scoped port method — `WikiStore`, the query ports, `DocumentRepository`, `IngestRunRepository`, `StudyProgressStore`, `QuizSessionStore`, `InteractionStore` — takes `kbId` as its **first** argument, and every adapter query binds it.
- The only exception is the sweep's system methods on `IngestRunRepository` (`findUnfinished`, `knowledgeBasesWithQueuedRuns`), which act for no user, return each row's `kbId`, and are called only by `RunWorker`.
- Every wiki, history and study table carries `knowledge_base_id`.
- Ownership ("does this user own this knowledge base?") is checked once per request in the controller or application service. The store **scopes**; it never authorizes.

```java
// CORRECT
Optional<WikiPage> findByPath(UUID kbId, String path);

// NEVER: a page lookup that can cross knowledge bases
Optional<WikiPage> findByPath(String path);  // ❌
```

## Data Store Roles

- **PostgreSQL**: The only data store. All business data, the wiki, its full history, all run records and the run queue.
- **Caffeine**: In-memory application cache (quiz sessions). No distributed cache dependency; appropriate for single-instance deployment.

There is no graph database and no vector store (ADR 0016).

## Ingest Runs and Idempotency

Nothing is skipped on a re-run — a re-run is a new run against the current wiki (ADR 0015). Every page-writing operation that calls a model (ingest, conversation edit, Lint):

1. Is inserted `QUEUED` in the transaction that asks for it, publishing `IngestRunQueued`
2. Is claimed: the knowledge base's lease (`active_run_id`) and `QUEUED` → `RUNNING` in **one** transaction; a lost claim leaves the run queued
3. Generates everything that needs a model **outside** any transaction
4. Commits pages, revisions, sources, links and the run's status in **one** `@Transactional` boundary, **fenced**: the run's status moves only from the expected status while it holds the lease, and 0 rows aborts the commit
5. Counts what it wrote from the rows it inserted — never from a model's report
6. Releases the lease in its terminal transaction, run from a `finally`

A **revert** makes no model call, so it is one transaction that claims the lease, writes and releases it — 409 while another run holds the lease.

```java
// CORRECT: fence first, then write; the run record and the pages land in one transaction
@Transactional
public void commitWrites(UUID kbId, UUID runId, List<PageWrite> writes, List<RunFailure> failures) {
    ingestRunRepository.markWritten(kbId, runId, failures);   // RUNNING → WRITTEN, or RunFencedException
    writes.forEach(w -> wikiStore.savePage(kbId, w, runId));
}

// NEVER: call a model inside the commit transaction (holds locks across minutes of LLM calls)
```

Every model call made by an `INGEST` or `LINT` run takes a permit from one global semaphore, so a burst of runs across knowledge bases cannot open the shared circuit breaker.

## Retrieval Cost Discipline

Order: **the rendered index first → a lexical prefilter only past 20K tokens of index → no vector store** (ADR 0016). Tokens are estimated by `TokenEstimate` (characters ÷ 3) and nowhere else.

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
PageDraft draft = pageWriter.write(task, existingBody, supersededSections, index);
List<LinkInsertion> links = linkChecker.check(bodies, index);
```

## Domain Events and Progress

There is no outbox table (ADR 0015). State changes and their domain events are published in the **same** `@Transactional` boundary through `EventPublisher`; listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)`.

- The run domain event is `IngestRunQueued`. Its listener wakes the worker. A missed event waits for the periodic sweep, never for redelivery, so listeners **must tolerate missing an event**.
- **Progress is not a domain event.** Services that run work call the best-effort `ProgressNotifier` port directly: at each step boundary, and for a status change only **after** its transaction returns, so no client sees a state that rolled back. Never set `fallbackExecution = true` to push progress through the event bus.
- `DomainEvent` requires only `occurredAt()`; each event record carries its own ids.
