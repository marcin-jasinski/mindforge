# Documentation Index

**IMPORTANT**: Read this file at the beginning of any development task to understand available documentation and standards.

## Quick Reference

### Project Documentation
Project-level documentation covering vision, goals, architecture, and technology choices. Files will be generated in the project initialization phase.

### Technical Standards
Coding standards, conventions, and best practices organized by domain (global, backend, frontend, testing).

---

## Project Documentation

Located in `docs/project/`

### Vision (`project/vision.md`)
What MindForge is and why it exists: a learning platform where uploaded documents are ingested into a per-knowledge-base wiki that compounds, with flashcards, quizzes and Query answers cut from the wiki and the wiki exportable as an OKF bundle. Covers the core value loop, principles and goals. Read this first for product context before feature work.

### Roadmap (`project/roadmap.md`)
Development phase status overview. Documents which phases (0–21, including 2b, 3b and 9b) are complete, in progress or planned, with brief summaries of what each delivers, and states plainly which phases the wiki re-cut changed, reused or removed. References the full detail in `project/implementation-plan.md`.

### Implementation Plan (`project/implementation-plan.md`)
Complete phase-by-phase breakdown of all development work: phases 0–21, each with detailed task lists, dependencies, completion checklists and rationale. This is the **source of truth** for structuring development work. Phase 4 (document parsing and ingestion) is where work resumes.

### Tech Stack (`project/tech-stack.md`)
Technology choices with rationale. Covers backend (Java 21, Spring Boot 4.1, Spring AI, Spring Data JPA/Hibernate, PostgreSQL as the only data store, Caffeine), frontend (Angular standalone SPA), bundle export, infrastructure (Docker, Flyway, Maven), and what was removed (Neo4j, pgvector, object storage). Read before introducing new dependencies or proposing technology changes.

### Architecture (`project/architecture.md`)
Navigable summary of the hexagonal architecture — layers, model services, the ingest/query/study/revert/export data flows, the wiki model, history and revert, idempotency and the ingest lease, retrieval, and the guard table ("every rule stated in the prompt and enforced in code"). Read before touching layer boundaries, adding adapters, or working on ingest.

---

## Domain Language and Decisions

### Glossary (`../CONTEXT.md`)
The ubiquitous language: Knowledge Base, Page, Page Path, Page Type, Concept, Source Summary, Section, Ingest Run, Page Revision, Tombstone, Revert, Supersession, Index, Bundle, Flashcard, Study Scope, Weak Page, and the operations Ingest (including the Conversation Edit), Query and Lint — with terms to avoid. Use these names in code, docs and conversation.

### Architecture Decision Records (`adr/`)
One file per load-bearing decision. 0001–0009 cover the original stack; 0010–0018 record the wiki re-cut (knowledge model, taxonomy and path identity, automatic revisions and revert, typed ingest pipeline, Postgres-only wiki storage, runs/lease/no outbox, index retrieval, Lint link insertions, flashcard identity). 0005 and 0006 are superseded by 0016.

### Wiki Re-cut Map (`wayfinder/okf-wiki-map.md`)
The decision map that produced the wiki re-cut: destination, every decision with a link to the ticket holding its full reasoning (`wayfinder/tickets/`), and what was ruled out of scope. Read a ticket's `## Answer` when you need the *why* behind an ADR.

### LLM Wiki Background (`wiki/`)
`llm_wiki.md` describes the LLM Wiki pattern MindForge adopts; `demo-transfer-notes.md` records what transferred from the reference implementation and why.

---

## Technical Standards

### Global Standards

Located in `docs/standards/global/`

#### Coding Style (`standards/global/coding-style.md`)
Naming consistency, automatic formatting, descriptive names, focused functions, no dead code, DRY principle.

#### Commenting (`standards/global/commenting.md`)
Let code speak, comment sparingly, no change comments.

#### Conventions (`standards/global/conventions.md`)
Predictable structure, clean version control, environment variables, minimal dependencies, testing standards, feature flags. Also covers: UTF-8 encoding, `npm@11` as the only permitted frontend package manager, LF line endings, and no-trailing-whitespace rules — all enforced via `.editorconfig`.

#### Error Handling (`standards/global/error-handling.md`)
Clear user messages, fail fast, typed exceptions, centralized handling, graceful degradation, retry with backoff, resource cleanup.

#### Minimal Implementation (`standards/global/minimal-implementation.md`)
Build what you need, no future stubs, no speculative abstractions, review before commit, delete exploration artifacts.

#### Validation (`standards/global/validation.md`)
Server-side always, validate early, specific errors, allowlists over blocklists, type and format checks, input sanitization, business rules.

---

### Backend Standards

Located in `docs/standards/backend/`

#### API Design (`standards/backend/api.md`)
RESTful principles, consistent naming, versioning, plural nouns, limited nesting, query parameters, proper status codes, rate limit headers. Also covers Spring MVC conventions: thin `@RestController` methods, constructor injection (never field `@Autowired`), and virtual thread blocking rules. Also covers the DTO layer: `api/dto/response/` and `api/dto/request/` records, forbidden response fields, and MapStruct `api/mapper/` DtoMappers (domain → DTO only, no entity types).

#### Java Conventions (`standards/backend/java-conventions.md`)
MindForge-specific Java conventions: package and import ordering, class-level `private static final` constants (SCREAMING_SNAKE_CASE), 79-char section dividers, `log` (not `logger`) naming for SLF4J loggers, domain-specific exception hierarchy, `record` types for value objects and results, and `sealed interface` for discriminated unions. Read before writing any new Java class in the `dev.mindforge` package.

#### Model Service Standards (`standards/backend/ai_agents.md`)
How code that calls an LLM is written:

- concrete model services in `dev.mindforge.agent`, with no `Agent` interface;
- a `VERSION` constant, bumped only on logic or prompt changes and recorded on every run in `step_versions`;
- model selection by `ModelTier` enum (`LARGE`, `SMALL`, `VISION`), never by provider string;
- all calls single-shot through `AIGateway`;
- prompt file naming;
- the "every rule stated in the prompt is enforced in code" discipline — code owns type, path, membership and deletion;
- lesson identity resolution.

#### Models (`standards/backend/models.md`)
Clear naming, timestamps, database constraints, appropriate types, index foreign keys, multi-layer validation, clear relationships, practical normalization. Also covers MapStruct: `@Mapper(componentModel = "spring")` interfaces for entity↔domain mapping, no manual `toEntity`/`toDomain` methods in adapters.

#### Database Queries (`standards/backend/queries.md`)
Parameterized queries, avoid N+1, select only needed columns, index strategic columns, transactions, query timeouts, cache expensive queries.

#### Database Migrations (`standards/backend/migrations.md`)
Reversible migrations, small and focused, zero-downtime awareness, separate schema and data, careful indexing, descriptive names, version control.

#### OpenAPI (`standards/backend/openapi.md`)
springdoc-openapi annotation conventions (`@Operation`, `@ApiResponse`, `@Schema`), forbidden fields in schemas, the contract-first workflow, and the `openapi-typescript` generation command for the Angular frontend.

---

### Frontend Standards

Located in `docs/standards/frontend/`

#### Components (`standards/frontend/components.md`)
Single responsibility, reusability, composability, clear interface, encapsulation, consistent naming, local state, minimal props.

#### Angular Patterns (`standards/frontend/angular-patterns.md`)
Angular 21 patterns for MindForge: standalone components only (no NgModules), `inject()` function for DI (not constructor injection), `signal()` and `computed()` for all component state (no `BehaviorSubject` for local state), `asReadonly()` for service-level shared signals, `HttpClient` via Angular's functional injection in services. Read before creating or modifying any Angular component or service.

#### CSS (`standards/frontend/css.md`)
Consistent methodology, work with the framework, design tokens, minimize custom CSS, production optimization.

#### Accessibility (`standards/frontend/accessibility.md`)
Semantic HTML, keyboard navigation, color contrast (4.5:1), alt text and labels, screen reader testing, ARIA when needed, heading structure, focus management.

#### Responsive Design (`standards/frontend/responsive.md`)
Mobile-first, standard breakpoints, fluid layouts, relative units, cross-device testing, touch-friendly targets (44x44px minimum), content priority.

---

### Architecture Standards

Located in `docs/standards/architecture/`

#### Hexagonal Architecture (`standards/architecture/hexagonal.md`)
Non-negotiable rules for MindForge's Hexagonal Architecture (Ports and Adapters). Covers:

- layer boundaries and forbidden cross-layer imports (`dev.mindforge.domain` must not import framework/I/O classes), including the model-services package;
- composition root placement (`@Configuration` beans, no static singletons);
- where Open/Closed applies — parsers and auth providers, but not the deliberately closed ingest pipeline;
- the persistence sub-package convention (`entity/`, `jpa/`, `mapper/`, `adapter/`);
- structural tenancy (`kbId` first on every tenant-scoped port method);
- data store roles (PostgreSQL only, plus Caffeine);
- ingest runs and idempotency (lease; generate outside, commit in one transaction);
- retrieval cost discipline (rendered index first, lexical prefilter past 20K tokens, no vector store);
- model-service communication (no service calls another);
- domain events after commit, with no outbox.

---

### Security Standards

Located in `docs/standards/security/`

#### Web Security (`standards/security/web-security.md`)
MindForge-specific security rules: server-authoritative state (fields forbidden in API responses: `reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`, `cost`), bcrypt password hashing via Spring Security's `BCryptPasswordEncoder` at cost ≥ 12, JWT in HttpOnly/Secure/SameSite=Lax cookies (never response body), OAuth CSRF handled by Spring Security OAuth2 client (never disabled), uploaded filename and URL sanitization, and resource ownership checks in every `@RestController` method. Read before any auth, file upload, or API response work.

---

### Testing Standards

Located in `docs/standards/testing/`

#### Test Writing (`standards/testing/test-writing.md`)
Test behavior not implementation, clear names, mock external dependencies, fast unit tests, risk-based testing, critical path focus, appropriate depth. Also covers Java testing conventions: test folder structure (`unit/`, `integration/`, `e2e/`), no Spring context in unit tests, `StubAIGateway` as the LLM test double, Mockito for port mocking, `make*` factory methods for domain objects and services, Testcontainers for integration tests, and AssertJ fluent assertions.

---

## How to Use This Documentation

1. **Start Here**: Always read this INDEX.md first to understand what documentation exists
2. **Project Context**: Read relevant project documentation before starting work
3. **Standards**: Reference appropriate standards when writing code
4. **Keep Updated**: Update documentation when making significant changes
5. **Customize**: Adapt all documentation to the project's specific needs

## Updating Documentation

- Project documentation should be updated when goals, tech stack, or architecture changes
- Technical standards should be updated when team conventions evolve
- Always update INDEX.md when adding, removing, or significantly changing documentation
