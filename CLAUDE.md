# MindForge — Claude Code Instructions

## First Step for Every Task

Read `docs/INDEX.md` before starting any work. It lists all available project documentation and standards with concise descriptions of what each file covers. Use it to find the right standards for the task at hand.

---

## Project Overview

**MindForge** is an AI-powered learning platform. Uploaded documents are ingested into a per-knowledge-base wiki of LLM-written pages that compounds with every upload; flashcards, quizzes and Query answers are cut from the wiki, and the wiki exports as an OKF bundle. Vocabulary: `CONTEXT.md`. Decisions: `docs/adr/` (0010–0018 cover the wiki re-cut).

- **Backend**: Java 21 + Spring Boot 4.1, Spring AI, Spring Data JPA/Hibernate, Flyway, Maven
- **Frontend**: Angular 21 standalone SPA, Angular Material, Cytoscape.js, Signals
- **Databases**: PostgreSQL (the only data store), Caffeine (in-process cache). No Neo4j, no pgvector.
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
| Model services | `dev.mindforge.agent` | domain + `infrastructure.ai.*` — concrete services that call a model; no `Agent` interface |
| Adapters | `dev.mindforge.api`, `dev.mindforge.cli` | all layers (thin; no business logic) |

Full rules: `docs/standards/architecture/hexagonal.md`

---

## Non-Negotiable Rules

These apply to every code change regardless of scope.

### Architecture
- **Never import framework/I/O classes into `dev.mindforge.domain`** — JDK only
- **Never modify `ParserRegistry` to add a document format** — register a new parser instead (Open/Closed). The ingest pipeline is deliberately closed: a new step is a design change, not a plugin
- **All Spring beans wired via `@Configuration`** — no static-init singletons
- **A run's commit and its domain events in the same `@Transactional` boundary** — listeners run after commit and must tolerate missing an event; there is no outbox table
- **Never call a model inside a database transaction** — generate first, then commit once
- **Every tenant-scoped port method takes `kbId` first** (`WikiStore`, the query ports, `DocumentRepository`, `IngestRunRepository`, the study and interaction stores) — tenancy is structural, not a filter to remember

### API / Controllers
- **Thin controllers only** — input validation + auth check + delegate to application service; no business logic
- **Constructor injection always** — never `@Autowired` on fields
- **Virtual threads are enabled** — blocking I/O in controllers is fine; never introduce reactive types

### Model Services
- **All LLM calls through `AIGateway`** — never instantiate a provider SDK directly
- **Request models by role** (`ModelTier.LARGE`, `ModelTier.SMALL`, `ModelTier.VISION`) — never by provider string
- **`VERSION` bumped only on logic/prompt change** — not for style fixes; it is recorded on every run
- **Every rule a prompt teaches is enforced in code** — the model proposes content; code owns page type, path, title, sources, membership and deletion, and counts what was written from inserted rows
- **The LLM is the sole author of page prose** — no code path lets a human edit a body; Lint writes only link insertions

### Security (read `docs/standards/security/web-security.md` before any auth/upload work)
- **Never return** `reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`, `cost` in API responses
- **JWT in HttpOnly/Secure/SameSite=Lax cookies** — never in response body
- **BCrypt cost ≥ 12** via Spring Security `BCryptPasswordEncoder`
- **Every `@RestController` method must verify resource ownership**
- **Study data never enters a wiki page or an export** — reference answers live only in quiz sessions; the exporter never reads study tables

### Java Conventions
- Logger: `private static final Logger log = LoggerFactory.getLogger(MyClass.class)` — named `log`, not `logger`
- Use `record` for value objects, domain events, result types
- Use `sealed interface` for discriminated unions (e.g., `StudyScope`)
- Domain exceptions extend meaningful base classes; never throw bare `RuntimeException` from business logic

### Testing
- **Unit tests must not load a Spring context** — plain Mockito, no `@SpringBootTest`
- **Use `StubAIGateway`** for deterministic LLM responses — never mock at the `ChatClient` level
- **Use `@Testcontainers`** with real PostgreSQL for integration tests
- **AssertJ** fluent assertions — never bare `assertEquals`
- **`make*` static factory methods** for domain objects in tests, not `@BeforeEach` fixtures

---

## Key Standards Files

| Topic | File |
|---|---|
| Architecture boundaries | `docs/standards/architecture/hexagonal.md` |
| Java code conventions | `docs/standards/backend/java-conventions.md` |
| API & Spring MVC | `docs/standards/backend/api.md` |
| Model services (LLM calls) | `docs/standards/backend/ai_agents.md` |
| Domain glossary | `CONTEXT.md` |
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

See `docs/project/roadmap.md` for which phases (0–21, including 2b, 3b and 9b) are complete and what work remains. Work resumes at Phase 4 (document parsing and ingestion).
See `docs/project/implementation-plan.md` for the full phase-by-phase task breakdown.

---

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`github.com/marcin-jasinski/mindforge`); external PRs are not a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical label strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo — one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
