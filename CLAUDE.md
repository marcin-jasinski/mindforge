# MindForge — Claude Code Instructions

## First Step for Every Task

Read `docs/INDEX.md` before starting any work. It lists all available project documentation and standards with concise descriptions of what each file covers. Use it to find the right standards for the task at hand.

---

## Project Overview

**MindForge** is an AI-powered learning platform that transforms uploaded documents into study artifacts (summaries, flashcards, concept maps, quizzes, knowledge graph).

- **Backend**: Java 21 + Spring Boot 3.2, Spring AI, Spring Data JPA/Hibernate, Flyway, Maven
- **Frontend**: Angular 21 standalone SPA, Angular Material, Cytoscape.js, Signals
- **Databases**: PostgreSQL (source of truth), Neo4j (derived read projection), Caffeine (in-process cache)
- **Build**: `mvn package` produces a single deployable JAR with the Angular build embedded
- **Tests**: JUnit 5, Mockito, Testcontainers, AssertJ

Full rationale: `docs/project/tech-stack.md`

---

## Architecture: Hexagonal (Ports and Adapters)

Dependencies always point **inward**: adapters → application → domain.

| Layer | Package | Allowed imports |
|---|---|---|
| Domain | `dev.mindforge.domain` | JDK only — zero I/O, zero framework |
| Application | `dev.mindforge.application` | `dev.mindforge.domain.*` only |
| Infrastructure | `dev.mindforge.infrastructure` | domain + application + any third-party |
| Agents | `dev.mindforge.agent` | domain + `infrastructure.ai.*` |
| Adapters | `dev.mindforge.api`, `dev.mindforge.cli` | all layers (thin; no business logic) |

Full rules: `docs/standards/architecture/hexagonal.md`

---

## Non-Negotiable Rules

These apply to every code change regardless of scope.

### Architecture
- **Never import framework/I/O classes into `dev.mindforge.domain`** — JDK only
- **Never modify the orchestrator to add a new agent or parser** — register a new adapter instead (Open/Closed)
- **All Spring beans wired via `@Configuration`** — no static-init singletons
- **Pipeline checkpoint + outbox event in the same `@Transactional` boundary**

### API / Controllers
- **Thin controllers only** — input validation + auth check + delegate to application service; no business logic
- **Constructor injection always** — never `@Autowired` on fields
- **Virtual threads are enabled** — blocking I/O in controllers is fine; never introduce reactive types

### AI Agents
- **All LLM calls through `AIGateway`** — never instantiate a provider SDK directly
- **Request models by role** (`"large"`, `"small"`, `"vision"`) — never by provider string
- **`VERSION` bumped only on logic/prompt change** — not for style fixes

### Security (read `docs/standards/security/web-security.md` before any auth/upload work)
- **Never return** `reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`, `cost` in API responses
- **JWT in HttpOnly/Secure/SameSite=Lax cookies** — never in response body
- **BCrypt cost ≥ 12** via Spring Security `BCryptPasswordEncoder`
- **Every `@RestController` method must verify resource ownership**

### Java Conventions
- Logger: `private static final Logger log = LoggerFactory.getLogger(MyClass.class)` — named `log`, not `logger`
- Use `record` for value objects, domain events, result types
- Use `sealed interface` for discriminated unions (e.g., `AgentResult`)
- Domain exceptions extend meaningful base classes; never throw bare `RuntimeException` from business logic

### Testing
- **Unit tests must not load a Spring context** — plain Mockito, no `@SpringBootTest`
- **Use `StubAIGateway`** for deterministic LLM responses — never mock at the `ChatClient` level
- **Use `@Testcontainers`** with real PostgreSQL/Neo4j for integration tests
- **AssertJ** fluent assertions — never bare `assertEquals`
- **`make*` static factory methods** for domain objects in tests, not `@BeforeEach` fixtures

---

## Key Standards Files

| Topic | File |
|---|---|
| Architecture boundaries | `docs/standards/architecture/hexagonal.md` |
| Java code conventions | `docs/standards/backend/java-conventions.md` |
| API & Spring MVC | `docs/standards/backend/api.md` |
| AI agent interface | `docs/standards/backend/ai_agents.md` |
| JPA models | `docs/standards/backend/models.md` |
| Database queries | `docs/standards/backend/queries.md` |
| Flyway migrations | `docs/standards/backend/migrations.md` |
| Security | `docs/standards/security/web-security.md` |
| Testing (JUnit 5) | `docs/standards/testing/test-writing.md` |
| Angular patterns | `docs/standards/frontend/angular-patterns.md` |
| Error handling | `docs/standards/global/error-handling.md` |
| Minimal implementation | `docs/standards/global/minimal-implementation.md` |

---

## Project State

See `docs/project/roadmap.md` for which phases (0–19) are complete and what work remains.
See `docs/project/implementation-plan.md` for the full phase-by-phase task breakdown.
