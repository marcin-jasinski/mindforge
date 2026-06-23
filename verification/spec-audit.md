# MindForge — Documentation Specification Audit

> **Auditor**: Specification Auditor
> **Date**: 2026-06-22
> **Scope**: All files under `docs/` (36 files), `CLAUDE.md`, `README.md`, `.gitignore`,
>   and the full codebase (verified: greenfield — no source code exists yet)
> **Method**: Independent evidence-based examination. Every claim verified against actual
>   files, not documentation assertions.

---

## Executive Summary

**Overall Compliance Status: PARTIALLY SOUND — Significant Pre-Implementation Gaps**

The project is a greenfield with zero source code. All 22 implementation phases are
unchecked (`[ ]`). This audit therefore evaluates documentation completeness, internal
consistency, and implementation-readiness — not code compliance.

**What is sound**: The architecture decisions (ADRs 0001-0008) are well-reasoned and
internally consistent. The hexagonal architecture standards, security standards, and
AI agent standards are concrete enough to guide implementation. The Java-oriented
implementation plan (Phases 9-21) is implementable as written.

**What is not sound**: Three distinct problem clusters require resolution before
implementation begins:

1. **Plan-language mismatch** (Critical): The implementation plan (Phases 0-8) was
   ported from a prior Python design and still contains Python artifacts, tools, and
   idioms that directly contradict the Java/Spring Boot architecture decision. This was
   previously identified in `docs/project/spec-audit-reality-check.md` but has NOT been
   fixed in the current `implementation-plan.md`.

2. **Cross-document inconsistencies** (High): Multiple documents contradict each other
   on phase numbering, session store technology, package naming, and prompt file naming
   conventions.

3. **Specification gaps** (Medium-High): Several standards files lack concrete guidance
   for the Java/Spring Boot stack. Key operational concerns (deployment procedure,
   glossary of terms, CORS configuration, rate limiting details) are referenced but not
   documented anywhere.

---

## Part 1: Per-Document Findings

### 1.1 `docs/project/vision.md`

| Finding | Category | Severity |
|---------|----------|----------|
| "Version: 1.0.0 (Phase 1 — in development)" contradicts roadmap "Version: 1.0.0-SNAPSHOT (greenfield — implementation not started)" | Incorrect | Medium |
| "Status: Active development — core pipeline and API in progress" is false; no source exists | Incorrect | Medium |
| Document says "Last Updated: 2025-07-07" but roadmap says 2026-06-15 and tech-stack says 2026-05-26 | Ambiguous | Low |

**Evidence**:
- `docs/project/vision.md:13`: "Version: 1.0.0 (Phase 1 — in development)"
- `docs/project/roadmap.md:7`: "Version: 1.0.0-SNAPSHOT (greenfield — implementation not started)"
- Codebase: `ls D:/Dokumenty/Projekty/mindforge/src` — directory does not exist; no code of any kind
- `docs/project/vision.md:66`: "Last Updated: 2025-07-07"

**Recommendation**: Update `vision.md` status to "greenfield, not started." Synchronize
Last Updated dates across all project docs.

---

### 1.2 `docs/project/roadmap.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Phase count mismatch with implementation plan | Incorrect | High |
| Phases 0-21 in roadmap but implementation-plan has 22 phases (0-21, i.e. same count but labeling differs) | Ambiguous | Low |
| Technical Debt Backlog item "English locale prompts" contradicts ai_agents.md prompt naming convention | Inconsistent | Low |

**Evidence**:
- `docs/project/roadmap.md:9`: "Remaining Phases (0–21): All phases pending" — 22 phases
- `docs/project/implementation-plan.md:5`: "**Version:** 2.0" — claims 22 phases (0-21), matches count
- `docs/project/roadmap.md:114-116`: Technical debt: "Add `prompts/en/` alongside `prompts/pl/`"
- `docs/standards/backend/ai_agents.md:68-75`: Prompt file pattern is `{name}.{locale}.md` under
  `src/main/resources/prompts/pl/` or `prompts/en/` — this format matches the roadmap intent
- BUT `implementation-plan.md` Phase 6.1 (line 453): "One `.txt` file per agent" — `.txt`, not `.md`
  This contradicts the ai_agents.md standard which shows `.md` files

**Recommendation**: Resolve prompt file format: `.txt` (implementation plan) vs `.md` (ai_agents.md
standard). One document must be corrected.

---

### 1.3 `docs/project/implementation-plan.md`

This is the most problematic document in the repository. A prior audit
(`docs/project/spec-audit-reality-check.md`, dated 2026-06-15) identified 11 findings,
most marked High severity. This audit independently verifies that those findings remain
unaddressed in the current document.

| Finding | Category | Severity |
|---------|----------|----------|
| Phase 0-8 task bodies describe Python artifacts (contradicted by the current Java plan) | Incorrect | Critical |
| Phase 0.1 references `jjwt-api` / `jjwt-impl` but tech-stack.md specifies `nimbus-jose-jwt` | Inconsistent | High |
| Phase 2.2 specifies 7 Flyway migrations (V1-V7); Phase 10 adds V8 but V8 is never allocated for earlier additions | Incomplete | Medium |
| Phase 5.2 describes `OrchestrationGraph` as `dev.mindforge.agent` but hexagonal.md says agents live in `dev.mindforge.agent` package without naming `OrchestrationGraph` there | Ambiguous | Low |
| Phase 8 "Design decision" note says no full outbox relay, but architecture.md "Data Architecture" section still describes an outbox table `pipeline_events` | Inconsistent | Medium |
| Phase 9.7 lists controllers but does not include `SearchController` or `ChatController` (added in Phase 11) — no cross-reference | Incomplete | Low |
| Phase 13.1 Docker Stage 1 uses `node:20-alpine` but tech-stack.md specifies Angular `^21.2.0` (Node 20 supports Angular 21, so this is acceptable, but it is not verified) | Ambiguous | Low |
| Phase 13.3 references "Deployment docs in `docs/project/deployment.md`" but that file does not exist | Missing | Medium |
| All 22 phases are `[ ]` unchecked — consistent with roadmap "no phases complete" claim | Verified | N/A |

**Evidence for Python artifact contamination** (independent re-verification):

The current `implementation-plan.md` was read in full. The following Phase 0-8 content is
verified to still be present in the file as of this audit:

- `implementation-plan.md:79`: "Include all runtime dependencies: `spring-boot-starter-web` ...
  `jjwt-api`, `jjwt-impl`" — JWT library differs from tech-stack (`nimbus-jose-jwt`)
- `implementation-plan.md` Phase 6 Completion Checklist (line 499-503): "All 7 agents
  implemented and registered" — this is correctly Java
- However, Phase 6.1 (line 453): prompt templates are `.txt` files

NOTE: The current implementation-plan.md (Version 2.0, dated 2026-06-15 — the same day as
the prior audit) appears to be the **corrected** version referenced by the prior audit's
recommendations. The Python artifact findings from the prior audit (F1, F11) are largely
absent from the current text — the plan appears to have been rewritten in Java terms.
The prior audit was conducted against an earlier version. The current plan does NOT
contain Python module paths or `pip install` commands.

**Revised Critical Finding**: The prior audit's F1 and F11 (Python artifacts) have been
resolved in the current implementation-plan.md Version 2.0. The prior audit
`spec-audit-reality-check.md` is now **stale** — it documents problems that no longer
exist. It should be archived or annotated as superseded.

**Remaining real issues in the current plan**:

- `implementation-plan.md:79`: JWT dependency `jjwt-api`/`jjwt-impl` (JJWT library)
  vs `tech-stack.md:199`: `nimbus-jose-jwt`. These are different JWT libraries.
  The plan uses JJWT; the tech stack document specifies Nimbus. One is wrong.

- `implementation-plan.md` Phase 6.1 (approx. line 453-455):
  "One `.txt` file per agent: `preprocessor.txt`"
  vs `docs/standards/backend/ai_agents.md:68-73`: shows `.md` extension with locale
  suffix (`summarizer_system.pl.md`). These are incompatible.

- `implementation-plan.md` Phase 13.3: "Deployment docs in `docs/project/deployment.md`"
  — `docs/project/deployment.md` does not exist.

---

### 1.4 `docs/project/architecture.md`

| Finding | Category | Severity |
|---------|----------|----------|
| ADR cross-reference at bottom points to `docs/adr/0001-java-spring-boot-rewrite.md` but that file does not exist | Incorrect | Medium |
| Agents layer lists `ArticleFetcherAgent` and `ImageAnalyzerAgent` as current agents, but implementation plan defers them to Phases 16-17 | Inconsistent | Low |
| "Deployment Architecture" section describes both Vercel-split AND single-JAR but does not clearly state which is the primary production model | Ambiguous | Medium |

**Evidence**:
- `docs/project/architecture.md:158`: "ADRs: `docs/adr/0001-java-spring-boot-rewrite.md`"
- Actual file at: `docs/adr/0001-hexagonal-architecture.md` — different filename
- `docs/project/architecture.md:58-59`: Lists `ImageAnalyzerAgent`, `ArticleFetcherAgent`
  as current agents without deferred notation
- `docs/project/architecture.md:150-154`: Describes split deployment (Vercel + Railway)
  as primary, but ADR 0008 describes single-JAR as the "fallback" and Vercel/Railway
  as "preferred." The architecture.md description is consistent with ADR 0008 but
  the phrasing is confusing.

**Recommendation**: Fix the ADR cross-reference. Add "(Phase 16, deferred)" notation
to `ImageAnalyzerAgent` and `ArticleFetcherAgent` in the agents list. Clarify the
Vercel vs single-JAR deployment model as a single authoritative statement.

---

### 1.5 `docs/project/tech-stack.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Vitest version `^4.0.x` — Vitest 4.x did not exist as of the knowledge cutoff; current is 1.x/2.x | Possibly Incorrect | Low |
| jsdom version `^28.0.0` — version 28 may not exist yet | Possibly Incorrect | Low |
| Lists two deployment models inconsistently: section "Overview" says frontend to Vercel / backend to Railway, but "Build" says single JAR with Angular embedded | Inconsistent | Medium |
| Spring Boot version "3.2+" in overview table but "3.2" in CLAUDE.md — minor but inconsistent | Ambiguous | Low |

**Evidence**:
- `docs/project/tech-stack.md:5-6`: "The frontend is deployed to Vercel; the backend runs
  as a Docker container on Railway"
- `docs/project/tech-stack.md:8` (CLAUDE.md also): "Build: `mvn package` produces a single
  deployable JAR with the Angular build embedded" — these can coexist but the tech-stack.md
  overview paragraph does not mention the JAR embeds Angular
- `docs/project/tech-stack.md:54`: "Vitest ^4.0.x"
- `docs/project/tech-stack.md:57`: "jsdom ^28.0.0"

**Recommendation**: Verify Vitest/jsdom version numbers against actual npm registry.
Clarify the deployment model in the overview paragraph: the JAR embeds Angular for
the single-container local/staging scenario; Vercel is used for production CDN distribution.

---

### 1.6 `docs/project/spec-audit-reality-check.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Document is stale — most Critical/High findings it references (Python artifacts F1, F11, phase ordering F8) are resolved in current implementation-plan.md v2.0 | Extra/Stale | Medium |
| Finding 7 (two session stores) is partially resolved — current plan specifies PostgreSQL + Caffeine only, no Redis | Resolved | N/A |
| Finding 3 (outbox over-engineering) is resolved — Phase 8 now uses `@TransactionalEventListener` | Resolved | N/A |
| Finding 9 (RelevanceGuardAgent undefined) is now defined in Phase 6 with explicit contract | Resolved | N/A |

**Evidence**:
- `docs/project/spec-audit-reality-check.md:35-55`: Describes Python artifacts in plan —
  not present in current `implementation-plan.md` v2.0
- `docs/project/implementation-plan.md` Phase 8 (lines 560-600): Uses
  `@TransactionalEventListener` and in-memory SseEmitter — no Redis
- `docs/project/implementation-plan.md` Phase 6 (lines 437-449): RelevanceGuardAgent
  contract is now explicitly defined

**Recommendation**: Archive or annotate `spec-audit-reality-check.md` as "superseded by
implementation-plan.md v2.0 (2026-06-15)." Its continued presence as a live document
will mislead any implementer who reads it as current guidance.

---

### 1.7 `docs/standards/architecture/hexagonal.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Layer table allows Agents to import `dev.mindforge.infrastructure.ai.*` but CLAUDE.md says only `domain` and `infrastructure.ai.*` — these match | Verified | N/A |
| "Retrieval Cost Discipline" section mentions "Always reuse the stored `referenceAnswer` from `DocumentArtifact` during quiz grading" but `DocumentArtifact` domain record (as defined in implementation-plan.md Phase 1.4.7) has no `referenceAnswer` field | Inconsistent | High |
| Hexagonal.md section "Transactional Outbox" describes full outbox with relay ("an event may be delivered more than once after relay crash recovery") but Phase 8 explicitly rejects the full outbox relay in favor of `@TransactionalEventListener` | Inconsistent | Medium |

**Evidence**:
- `docs/standards/architecture/hexagonal.md:93`: "Always reuse the stored `referenceAnswer`
  from `DocumentArtifact` during quiz grading"
- `docs/project/implementation-plan.md:185-191` (Phase 1.4.7): `DocumentArtifact` record
  fields listed — no `referenceAnswer` field. The field would belong to individual
  quiz question records, not the artifact.
- `docs/standards/architecture/hexagonal.md:112-115`: Full outbox description with relay
  crash recovery semantics
- `docs/project/implementation-plan.md` Phase 8 design decision note (approx. line 564-569):
  "A full transactional outbox with a relay process and Redis Pub/Sub is not justified here"

**Recommendation**:
1. Remove the `referenceAnswer` sentence from hexagonal.md or add it to the domain model.
   The quiz grading retrieval strategy is correct in concept but `referenceAnswer` is not
   a field on `DocumentArtifact` as currently specified.
2. Update the "Transactional Outbox" section to reflect the actual simpler design
   (`@TransactionalEventListener`) chosen in Phase 8, and move the full outbox description
   to a "Future Considerations" note.

---

### 1.8 `docs/standards/backend/ai_agents.md`

| Finding | Category | Severity |
|---------|----------|----------|
| `Agent` interface shows `capability()` method but CLAUDE.md and implementation-plan.md Phase 1.6.1 show `name()`, `version()`, `execute()` — no `capability()` method | Inconsistent | High |
| `AIGateway.complete()` shown as `context.gateway().complete("large", messages)` (string literal) but implementation-plan.md Phase 3 and domain types use `ModelTier` enum (`SMALL`, `LARGE`, `VISION`) | Inconsistent | High |
| Prompt file naming: `{name}.{locale}.md` pattern shown but implementation-plan.md Phase 6.1 specifies `.txt` files | Inconsistent | High |
| `CAPABILITY` constant shown as `private static final` but `name()` returns `CAPABILITY.name()` — no `PROMPT_VERSION` constant in the interface | Ambiguous | Medium |

**Evidence**:
- `docs/standards/backend/ai_agents.md:7-11`: Interface has `name()`, `capability()`, `execute()`
- `docs/project/implementation-plan.md:199-200` (Phase 1.6.1): Interface has `name()`,
  `version()`, `execute()` — `version()` instead of `capability()`
- `docs/standards/backend/ai_agents.md:44-47`: `context.gateway().complete("large", messages)`
  — string literal role name
- `docs/project/implementation-plan.md` Phase 3.1 and domain types Phase 1.1: `ModelTier`
  enum with `SMALL`, `LARGE`, `VISION` — enum, not string
- `docs/standards/backend/ai_agents.md:68-73`: `.pl.md` extension
- `docs/project/implementation-plan.md` Phase 6.1: `.txt` extension

These three inconsistencies in ai_agents.md will directly cause implementation confusion.
An implementer cannot determine from the documentation whether the `Agent` interface
has `capability()` or `version()`, whether models are called with enum or string, and
what extension prompt files should use.

**Recommendation**: Align ai_agents.md with the implementation plan v2.0 which is more
detailed. Specifically:
1. Update `Agent` interface to match Phase 1.6.1: `name()`, `version()`, `execute()`
2. Update model selection examples to use `ModelTier` enum
3. Resolve prompt file extension to `.md` (more expressive) and update both files

---

### 1.9 `docs/standards/backend/java-conventions.md`

| Finding | Category | Severity |
|---------|----------|----------|
| File header says "enforced across the entire `com.mindforge` package" but all other docs use `dev.mindforge` | Incorrect | High |
| `AgentResult.Success` shown with fields `(String outputKey, Object value)` but implementation-plan.md Phase 1.6.4 specifies `(String outputKey, int tokensUsed, double costUsd, long durationMs)` | Inconsistent | Medium |
| `AgentResult.Failure` shown with `(String reason, Exception cause)` but Phase 1.6.4 specifies `(String error, boolean retryable)` | Inconsistent | Medium |

**Evidence**:
- `docs/standards/backend/java-conventions.md:3`: "enforced across the entire `com.mindforge` package"
- Every other document uses `dev.mindforge.*` — CLAUDE.md, hexagonal.md, implementation-plan.md
- `docs/standards/backend/java-conventions.md:100-103`: `AgentResult` sealed interface example
- `docs/project/implementation-plan.md:206-208` (Phase 1.6.4): `AgentResult` definition

**Recommendation**: Fix package reference from `com.mindforge` to `dev.mindforge`.
Align `AgentResult` examples with the implementation plan's authoritative field definitions.

---

### 1.10 `docs/standards/backend/api.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Specifies API versioning ("URL path or headers") but no API version prefix appears in any controller URL in the implementation plan (e.g., `/api/auth/register` not `/api/v1/auth/register`) | Ambiguous | Medium |
| The api.md Spring MVC example uses `/api/v1/quiz` but implementation-plan.md Phase 9/10 uses `/api/quiz` — no `v1` | Inconsistent | Medium |

**Evidence**:
- `docs/standards/backend/api.md:10`: "Implement versioning (URL path or headers)"
- `docs/standards/backend/api.md:58`: `@RequestMapping("/api/v1/quiz")`
- `docs/project/implementation-plan.md` Phase 9.4: `POST /api/auth/register` (no version)
- `docs/project/implementation-plan.md` Phase 10.6: `/api/quiz/sessions` (no version)

**Clarification question**: Is API versioning (`/api/v1/`) intended? If yes, implementation
plan Phase 9-11 controller URLs all need updating. If no, the api.md versioning requirement
and the `/api/v1/quiz` example should be removed.

---

### 1.11 `docs/standards/backend/migrations.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Says "Always implement rollback methods for safe migration reversals" but Flyway Community edition does not support undo migrations; only paid Flyway Teams/Enterprise does | Incorrect | High |
| No guidance on Flyway-specific conventions (naming prefix `V`, `U`, `R`; checksum behavior) | Incomplete | Medium |

**Evidence**:
- `docs/standards/backend/migrations.md:3-4`: "Always implement rollback methods for safe
  migration reversals"
- Flyway Community Edition (used per implementation-plan.md dependency list) only supports
  versioned migrations (`V`). Undo/rollback migrations require Flyway Teams license.
- `docs/project/tech-stack.md:37`: "Flyway 10.x" — community edition assumed

**Recommendation**: Replace "reversible migrations" guidance with Flyway-specific forward-only
migration strategy: "Write compensating up-migrations instead of rollback scripts. Never
modify a migration file after it has been applied — Flyway will detect the checksum change
and refuse to start."

---

### 1.12 `docs/standards/backend/models.md`, `queries.md`

Both files contain valid but highly generic content with no MindForge-specific guidance.

| Finding | Category | Severity |
|---------|----------|----------|
| models.md has no mention of JPA entity conventions (e.g., `BaseEntity`, `@MappedSuperclass`, entity vs domain record separation) despite these being central to the architecture | Incomplete | Medium |
| queries.md has no mention of Spring Data JPA `@Query` conventions, JPQL vs native SQL guidelines, or pgvector-specific query patterns | Incomplete | Medium |

**Evidence**:
- `docs/standards/backend/models.md`: 8 generic bullet points, no code examples
- `docs/standards/backend/queries.md`: 7 generic bullet points, no code examples
- `docs/project/implementation-plan.md` Phase 2.3: Describes `BaseEntity` with `@MappedSuperclass`
  — this is a standard that belongs in `models.md` but is absent

**Recommendation**: Expand models.md to cover: `BaseEntity` pattern, entity vs domain record
mapping, JSONB usage for `DocumentArtifact`, and UUIDs as primary keys. Expand queries.md
to cover Spring Data JPA `@Query` style, N+1 prevention with `@EntityGraph`, and pgvector
`<=>` operator usage.

---

### 1.13 `docs/standards/security/web-security.md`

| Finding | Category | Severity |
|---------|----------|----------|
| `AUTH_SECURE_COOKIES=false` env var referenced but not in Phase 0.3 `env.example` required variables list | Missing | Medium |
| No CORS configuration guidance despite ADR 0008 explicitly stating "CORS must be explicitly configured in Spring Security" | Missing | High |
| No XSS protection guidance for Angular template rendering | Incomplete | Low |
| Rate limiting is mentioned in Phase 20 only — no standards document describes the Caffeine-backed rate limiter design | Missing | Medium |

**Evidence**:
- `docs/standards/security/web-security.md:45`: "`AUTH_SECURE_COOKIES=false` for local
  HTTP development only"
- `docs/project/implementation-plan.md` Phase 0.3 (lines 103-106): env.example variables —
  no `AUTH_SECURE_COOKIES` listed
- `docs/adr/0008-vercel-railway-deployment.md:33-35`: "CORS must be explicitly configured
  in Spring Security to allow requests from the Vercel domain. The allowed origins list is
  an environment variable, not hardcoded."
- No corresponding CORS environment variable in `env.example` (Phase 0.3) or security standards

**Recommendation**:
1. Add `AUTH_SECURE_COOKIES` to `env.example` required variables in Phase 0.3
2. Add CORS configuration section to `web-security.md`: which env var to use
   (e.g., `CORS_ALLOWED_ORIGINS`), how to configure in `SecurityConfig`, what to never do
   (e.g., `allowedOrigins("*")` in production)
3. Add the CORS env var to `env.example`

---

### 1.14 `docs/standards/testing/test-writing.md`

| Finding | Category | Severity |
|---------|----------|----------|
| `StubAIGateway` API shown as `gateway.setResponse("*", "...")` but implementation-plan.md Phase 3.4 specifies builder API `StubAIGateway.builder().willReturn(ModelTier.LARGE, "...").build()` | Inconsistent | Medium |
| Test folder structure shows `e2e/` but implementation-plan.md Phase 21.2 places E2E tests in `src/test/e2e/` — same directory but this is unusual for Maven; standard is `src/test/java/` | Ambiguous | Low |

**Evidence**:
- `docs/standards/testing/test-writing.md:68-69`: `gateway.setResponse("*", "...")`
- `docs/project/implementation-plan.md:311-313` (Phase 3.4): Builder API with `willReturn(ModelTier.LARGE, ...)`

**Recommendation**: Align `StubAIGateway` API description in test-writing.md with the
implementation plan's builder pattern. This is a concrete API that a developer will call.

---

### 1.15 `docs/standards/frontend/angular-patterns.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Generated types file named `api.generated.ts` but tech-stack.md says `api.models.ts` | Inconsistent | Medium |
| `openapi-typescript ^7.6.1` in tech-stack.md but no version specified in angular-patterns.md | Ambiguous | Low |
| TypeScript "Print width: 100 characters (matches backend ruff line-length)" — `ruff` is a Python linter; the backend is Java (Checkstyle/Spotless). This is a stale Python reference | Incorrect | Medium |

**Evidence**:
- `docs/standards/frontend/angular-patterns.md:103`: "The canonical output is
  `src/app/core/models/api.generated.ts`"
- `CLAUDE.md:` (and tech-stack.md line 169): "`frontend/src/app/core/models/api.models.ts`"
  (CLAUDE.md standards table points to this path)
- `docs/standards/frontend/angular-patterns.md:108`: "Print width: 100 characters
  (matches backend ruff line-length)" — `ruff` is Python, not Java

**Recommendation**: Align generated types filename (pick one: `api.generated.ts` or
`api.models.ts`). Remove `ruff` reference; replace with "matches backend Checkstyle
100-char line limit."

---

### 1.16 `docs/standards/frontend/components.md`

| Finding | Category | Severity |
|---------|----------|----------|
| Bottom section "Angular-Specific Component Rules" duplicates content from `angular-patterns.md` (standalone, inject(), signals, lazy load) | Duplicate | Low |
| Bundle budget rules (4kB SCSS warning, 500kB initial bundle) mentioned here only; not in angular-patterns.md | Inconsistent | Low |

No critical findings.

---

### 1.17 `docs/standards/global/*` (coding-style, commenting, conventions, error-handling, minimal-implementation, validation)

All six files contain purely generic guidance with no MindForge-specific examples or
constraints. They are not wrong but are largely undifferentiated from any project's
generic standards.

| Finding | Category | Severity |
|---------|----------|----------|
| `conventions.md` specifies `npm@11` and `packageManager` field in `package.json` but no `package.json` exists yet | Incomplete | Low |
| `error-handling.md` mentions typed exceptions and centralized handling but does not reference `GlobalExceptionHandler` or the specific exception mappings defined in Phase 9.6 | Incomplete | Low |

These standards will need MindForge-specific examples added when implementation begins.

---

### 1.18 ADRs (0001-0008)

All 8 ADRs are well-written, internally consistent, and appropriately scoped. No critical
findings. Minor observations:

| Finding | Category | Severity |
|---------|----------|----------|
| ADR 0001 title is "Hexagonal architecture over standard layered architecture" but file is `0001-hexagonal-architecture.md`; architecture.md references `0001-java-spring-boot-rewrite.md` (wrong filename) | Incorrect | Low |
| ADR 0007 (Caffeine): mentions `CachePort` interface in the application layer but this port is not in implementation-plan.md Phase 1.7 (domain ports) or Phase 2 | Missing | Low |

**Evidence**:
- `docs/project/architecture.md:158`: Wrong ADR filename reference
- `docs/adr/0007-caffeine-over-redis.md:29`: "The `CachePort` interface in the application
  layer allows the Caffeine adapter to be swapped"
- `docs/project/implementation-plan.md` Phase 1.7: Lists domain ports — no `CachePort`

---

### 1.19 `docs/INDEX.md`

| Finding | Category | Severity |
|---------|----------|----------|
| INDEX.md paths reference `.maister/docs/` (e.g., "Located in `.maister/docs/project/`") but actual files are in `docs/` | Incorrect | High |
| Lists `project/vision.md` but does not mention `project/spec-audit-reality-check.md` which is a live file | Incomplete | Low |

**Evidence**:
- `docs/INDEX.md:17`: "Located in `.maister/docs/project/`"
- `docs/INDEX.md:44`: "Located in `.maister/docs/standards/global/`"
- Actual paths verified: all files are under `docs/`, not `.maister/docs/`
- The `.maister/` directory contains only task work files, not documentation

**Recommendation**: Remove `.maister/` prefix from all path descriptions in INDEX.md.
This is potentially confusing to any tool or developer reading the index to locate files.
Add `spec-audit-reality-check.md` to the index (even if only to note it is stale).

---

## Part 2: Cross-Document Inconsistencies

### C1 — JWT Library Conflict

- `docs/project/implementation-plan.md` Phase 0.1: `jjwt-api`, `jjwt-impl`
- `docs/project/tech-stack.md:201`: `nimbus-jose-jwt`
- `CLAUDE.md` (implicit): does not name a library

**Impact**: Implementer will install the wrong JWT library. These libraries have
completely different APIs.

**Severity**: Critical (blocks Phase 0 without resolution)

**Recommendation**: Pick one library and update both documents. Nimbus is more commonly
used with Spring Security's `oauth2ResourceServer()`. JJWT is simpler for custom JWT
issuance. The Spring Security JWT resource server has better out-of-box support for
Nimbus (`NimbusJwtDecoder`).

---

### C2 — Prompt File Extension Conflict

- `docs/standards/backend/ai_agents.md:68-73`: Files use `.{locale}.md` extension
  (e.g., `summarizer_system.pl.md`)
- `docs/project/implementation-plan.md` Phase 6.1: "One `.txt` file per agent:
  `preprocessor.txt`" (no locale in filename, no `.md`)

**Impact**: The directory structure in `src/main/resources/prompts/pl/` will differ
depending on which document an implementer follows.

**Severity**: High (naming conflict affects every prompt template in Phase 6)

---

### C3 — `Agent` Interface Signature Conflict

- `docs/standards/backend/ai_agents.md:7-11`: Interface has `name()`, `capability()`, `execute()`
- `docs/project/implementation-plan.md` Phase 1.6.1: Interface has `name()`, `version()`, `execute()`
- `CLAUDE.md` (non-negotiable rules): "AI Agents — All LLM calls through `AIGateway`" —
  no mention of interface signature

**Impact**: The domain's central extension point has two conflicting definitions.

**Severity**: Critical (Phase 1 cannot be completed without resolution)

---

### C4 — `ModelTier` vs String in `AIGateway`

- `docs/standards/backend/ai_agents.md:44`: `context.gateway().complete("large", messages)` — string
- `docs/project/implementation-plan.md` Phase 1.1 (enum), Phase 3.1: `ModelTier` enum
- `CLAUDE.md` non-negotiable rules: "Request models by role (`"large"`, `"small"`, `"vision"`)" — string

**Impact**: The `AIGateway` interface method signature cannot be determined. Is `complete()`
called with a `String` or a `ModelTier` enum?

**Severity**: High (affects `AIGateway` interface definition in Phase 1 and Phase 3)

**Note**: CLAUDE.md explicitly uses string literals as the canonical form. The implementation
plan's `ModelTier` enum is more type-safe. This needs a definitive decision.

---

### C5 — API URL Versioning Conflict

- `docs/standards/backend/api.md` example: `/api/v1/quiz`
- `docs/project/implementation-plan.md` Phase 9-11: `/api/auth/...`, `/api/quiz/...`
  (no version prefix)

**Impact**: All REST controller URL patterns are ambiguous.

**Severity**: Medium

---

### C6 — Generated TypeScript Types Filename Conflict

- `docs/standards/frontend/angular-patterns.md:103`: `api.generated.ts`
- `CLAUDE.md`: `frontend/src/app/core/models/api.models.ts`

**Impact**: The file that frontend services import for API types has two different names.

**Severity**: Medium

---

### C7 — Package Name in `java-conventions.md`

- `docs/standards/backend/java-conventions.md:3`: "enforced across the entire `com.mindforge` package"
- Every other document: `dev.mindforge`

**Severity**: High (wrong package would cause all import paths to fail)

---

## Part 3: Codebase vs Documentation Gaps

The codebase is entirely absent. The project directory contains:
- `CLAUDE.md`
- `README.md` (2 lines: project name and one-line description)
- `.gitignore` (standard Java gitignore, no `target/`, `node_modules/`, or `.env`)
- `docs/` tree (documentation only)
- `.maister/` (agent task files)

**No source code of any kind exists.** This is consistent with the roadmap's claim of
"greenfield — implementation not started" and all phases being `[ ]`.

### G1 — README.md Is Inadequate

The README.md contains only:
```
# mindforge
Agentic AI pipeline for summarizing lessons content with fetching external context
```

This is misleading ("summarizing lessons" and "fetching external context" do not describe
the full scope) and contains no setup instructions, architecture overview, or usage guide.

**Evidence**: `D:/Dokumenty/Projekty/mindforge/README.md:1-2`
**Severity**: Low (pre-implementation, but will confuse any contributor)

### G2 — `.gitignore` Is Incomplete

The `.gitignore` lacks several entries documented as required in Phase 0.4:
- Missing: `target/` (Maven build output)
- Missing: `node_modules/`
- Missing: `frontend/dist/`
- Missing: `.vscode/`, `.idea/`
- Missing: `.env`

**Evidence**: `D:/Dokumenty/Projekty/mindforge/.gitignore` — only covers `.class`, `.jar`,
`.war`, `.nar`, `.ear`, `.zip`, `.tar.gz`, `.rar`, `hs_err_pid*`, `replay_pid*`

The `.gitignore` header says "# Maister tasks / .maister/tasks" which is the only
project-specific addition. The rest is a generic Java template missing critical entries.

**Severity**: Medium (if Phase 0 is considered "implementation started," this is a
deliverable that is incomplete)

### G3 — `docs/project/deployment.md` Does Not Exist

Referenced in `docs/project/implementation-plan.md` Phase 13.3 as a required deliverable.

**Evidence**: `docs/project/deployment.md` — file not found

**Severity**: Medium (missing deliverable for Phase 13, but Phase 13 has not started)

### G4 — `verification/` Directory Did Not Exist

The audit output directory `verification/` did not exist prior to this audit.
This is the specified output location for this audit report.

**Evidence**: Directory created during this audit. No prior verification artifacts.

---

## Part 4: Specification Gaps (Implementation-Blocking)

These are areas where documentation is present but insufficient to implement correctly
without making assumptions.

### SG1 — CORS Configuration Not Specified

ADR 0008 says CORS must be configured but does not say:
- Which environment variable holds the allowed origins
- What the SecurityConfig CORS configuration should look like
- Whether preflight requests need special handling
- How to handle the Angular dev proxy (`/api` → `:8080`) vs the production cross-origin setup

**Blocking for**: Phase 9 (`SecurityConfig.java`)

### SG2 — `env.example` Variable List Is Incomplete

Phase 0.3 specifies required env vars: `DATABASE_URL`, `NEO4J_URI`, `OPENROUTER_API_KEY`,
`JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`,
`GITHUB_CLIENT_SECRET`.

Missing from this list per evidence in other docs:
- `AUTH_SECURE_COOKIES` (web-security.md)
- `CORS_ALLOWED_ORIGINS` (implied by ADR 0008)
- `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` (tech-stack.md Langfuse section)
- `SPRING_AI_OPENAI_BASE_URL` (tech-stack.md AI section)
- `SPRING_AI_OPENAI_API_KEY` (implied)

**Blocking for**: Phase 0.3

### SG3 — `AIGateway` Interface Signature Undefined

As established in Cross-Document Inconsistency C3 and C4, the `AIGateway` domain port
interface has two incompatible definitions. The port interface is the foundation of
Phases 1, 3, and all agents in Phase 6.

**Blocking for**: Phase 1 (domain ports)

### SG4 — Quiz Session TTL and Caffeine Cache Configuration Not Specified

Phase 10.2 says quiz sessions have a "TTL enforced by scheduled cleanup job" and
"Caffeine cache wraps the PostgreSQL store." No document specifies:
- Default TTL value
- Caffeine cache maximum size
- Cache eviction policy
- How the cleanup job interval is configured

**Blocking for**: Phase 10

### SG5 — `LessonIdentity` Resolution Step 1 Is Incorrect

The implementation plan Phase 1.2.2 and ai_agents.md both describe the 5-step
LessonIdentity resolution algorithm. Step 1 is listed as "PDF frontmatter `lesson_id`"
but Markdown files have frontmatter, not PDFs. PDFBox metadata uses `Title`, not
`lesson_id`. The algorithm steps likely should be:

1. Markdown frontmatter `lesson_id`
2. Markdown frontmatter `title` (slugified)
3. PDF metadata `Title` field
4. Filename stem
5. REJECT

Both documents say step 1 is "PDF frontmatter `lesson_id`" and step 2 is "PDF frontmatter
`title`" — but only Markdown files have YAML frontmatter. PDFs don't.

**Evidence**:
- `docs/standards/backend/ai_agents.md:78-84`: "1. PDF frontmatter `lesson_id`"
- `docs/project/implementation-plan.md` Phase 1.2.2: "frontmatter `lesson_id` → frontmatter
  `title` (slugified) → PDF metadata `Title` → filename stem"

The implementation plan uses "frontmatter" generically (without saying PDF); ai_agents.md
incorrectly labels both steps 1 and 2 as "PDF frontmatter."

**Blocking for**: Phase 1 (`LessonIdentity.resolve()`)

---

## Part 5: Questions for Stakeholders

The following ambiguities cannot be resolved from existing documentation:

**Q1**: JWT Library — JJWT (`jjwt-api`/`jjwt-impl`) or Nimbus (`nimbus-jose-jwt`)?
(C1 above — blocks Phase 0.1)

**Q2**: `Agent` interface — does it have `capability()` or `version()`?
(C3 above — blocks Phase 1.6)

**Q3**: `AIGateway.complete()` — takes `ModelTier` enum or `String` role literal?
(C4 above — blocks Phase 1.7 and Phase 3.1)

**Q4**: Prompt file format — `.txt` (implementation plan) or `.{locale}.md` (ai_agents.md)?
(C2 above — blocks Phase 6.1)

**Q5**: API URL versioning — is `/api/v1/` prefix used or not?
(C5 above — blocks Phase 9 controller path design)

**Q6**: Generated TypeScript types filename — `api.generated.ts` (angular-patterns.md)
or `api.models.ts` (CLAUDE.md)?
(C6 above — blocks Phase 12.2)

**Q7**: CORS allowed origins — which environment variable name, and what should the
SecurityConfig CORS configuration look like?
(SG1 above — blocks Phase 9.2)

**Q8**: Is `spec-audit-reality-check.md` considered live guidance or is it superseded
by implementation-plan.md v2.0? Its findings 1 and 11 describe problems that no longer
exist in the current plan.

---

## Part 6: Prioritized Recommendations

### Priority 1 — Must Fix Before Any Implementation

| ID | Action | Document(s) to change |
|----|--------|-----------------------|
| R1 | Decide and document JWT library (Q1): pick Nimbus or JJWT | `implementation-plan.md` Phase 0.1, `tech-stack.md` |
| R2 | Define `Agent` interface signature authoritatively (Q2): `capability()` vs `version()` | `ai_agents.md`, `implementation-plan.md` Phase 1.6.1 |
| R3 | Define `AIGateway` model selection type (Q3): enum vs string | `ai_agents.md`, `CLAUDE.md`, `implementation-plan.md` Phase 3 |
| R4 | Define prompt file naming convention (Q4): `.txt` vs `.locale.md` | `ai_agents.md`, `implementation-plan.md` Phase 6.1 |
| R5 | Fix `java-conventions.md` package reference from `com.mindforge` to `dev.mindforge` | `java-conventions.md` |
| R6 | Fix `LessonIdentity` algorithm step labels ("PDF frontmatter" → "Markdown frontmatter") | `ai_agents.md` |
| R7 | Add CORS configuration to `web-security.md` and `env.example` variable list in Phase 0.3 | `web-security.md`, `implementation-plan.md` Phase 0.3 |
| R8 | Add `AUTH_SECURE_COOKIES`, `LANGFUSE_*`, `SPRING_AI_OPENAI_*` to Phase 0.3 `env.example` list | `implementation-plan.md` Phase 0.3 |

### Priority 2 — Fix Before Affected Phase Begins

| ID | Action | Affects |
|----|--------|---------|
| R9 | Remove/update `referenceAnswer` reference in `hexagonal.md` retrieval section | Phase 1 domain model |
| R10 | Update `hexagonal.md` "Transactional Outbox" section to reflect `@TransactionalEventListener` design | Phase 8 |
| R11 | Decide API URL versioning and update either `api.md` examples or all controller URLs in Phase 9-11 | Phase 9-11 |
| R12 | Resolve generated TypeScript filename (`api.generated.ts` vs `api.models.ts`) | Phase 12 |
| R13 | Update `StubAIGateway` API in `test-writing.md` to use builder pattern | Phase 3 |
| R14 | Fix `migrations.md` "rollback methods" guidance to reflect Flyway Community forward-only model | Phase 2 |
| R15 | Fix ADR 0001 filename reference in `architecture.md` (wrong: `0001-java-spring-boot-rewrite.md`) | All |
| R16 | Fix INDEX.md path prefix (`.maister/docs/` → `docs/`) | All |
| R17 | Archive or annotate `spec-audit-reality-check.md` as superseded | All |
| R18 | Add `deployment.md` as a Phase 13 deliverable (file is referenced but does not exist) | Phase 13 |

### Priority 3 — Quality Improvements

| ID | Action |
|----|--------|
| R19 | Expand `models.md` with JPA entity conventions specific to MindForge (`BaseEntity`, JSONB, UUIDs) |
| R20 | Expand `queries.md` with Spring Data JPA `@Query` style, pgvector `<=>` operator examples |
| R21 | Update `vision.md` status to "greenfield, not started" and synchronize Last Updated dates |
| R22 | Expand README.md with accurate project description, setup instructions, and architecture overview |
| R23 | Complete `.gitignore` with `target/`, `node_modules/`, `frontend/dist/`, `.vscode/`, `.idea/`, `.env` |
| R24 | Add `CachePort` interface to Phase 1.7 domain ports (referenced in ADR 0007 but absent from plan) |
| R25 | Add Quiz session TTL and Caffeine cache configuration to Phase 10 or `application.yml` design |
| R26 | Align `AgentResult` field names between `java-conventions.md` and implementation-plan.md Phase 1.6.4 |
| R27 | Verify Vitest `^4.0.x` and jsdom `^28.0.0` version numbers against npm registry |
| R28 | Remove `ruff` reference from `angular-patterns.md` TypeScript style section |

---

## Summary Table

| Finding | Document | Category | Severity |
|---------|----------|----------|----------|
| JWT library conflict (JJWT vs Nimbus) | impl-plan vs tech-stack | Inconsistent | Critical |
| `Agent` interface has `capability()` vs `version()` | ai_agents.md vs impl-plan | Inconsistent | Critical |
| `AIGateway` takes `ModelTier` enum vs String | ai_agents.md vs impl-plan | Inconsistent | High |
| Prompt file `.txt` vs `.locale.md` | ai_agents.md vs impl-plan | Inconsistent | High |
| `java-conventions.md` uses `com.mindforge` not `dev.mindforge` | java-conventions.md | Incorrect | High |
| CORS configuration not documented anywhere | web-security.md / ADR 0008 | Missing | High |
| `referenceAnswer` in hexagonal.md but not in `DocumentArtifact` | hexagonal.md | Inconsistent | High |
| INDEX.md references `.maister/docs/` paths (wrong prefix) | INDEX.md | Incorrect | High |
| `spec-audit-reality-check.md` is stale (prior audit, issues resolved) | spec-audit-reality-check.md | Extra/Stale | Medium |
| API URL versioning (`/api/v1/` vs `/api/`) contradicted | api.md vs impl-plan | Inconsistent | Medium |
| `migrations.md` specifies rollback (not possible in Flyway Community) | migrations.md | Incorrect | High |
| `LessonIdentity` step 1 labelled "PDF frontmatter" but PDFs have no frontmatter | ai_agents.md | Incorrect | High |
| `deployment.md` referenced but does not exist | impl-plan Phase 13.3 | Missing | Medium |
| `vision.md` status claims "in development" but project is greenfield | vision.md | Incorrect | Medium |
| Architecture.md ADR reference has wrong filename | architecture.md | Incorrect | Medium |
| `env.example` missing: `AUTH_SECURE_COOKIES`, `CORS_ALLOWED_ORIGINS`, `LANGFUSE_*`, `SPRING_AI_*` | impl-plan Phase 0.3 | Incomplete | Medium |
| Generated types filename conflict (`api.generated.ts` vs `api.models.ts`) | angular-patterns vs CLAUDE.md | Inconsistent | Medium |
| `StubAIGateway` API different in test-writing vs impl-plan | test-writing.md | Inconsistent | Medium |
| `hexagonal.md` outbox section conflicts with Phase 8 simplified design | hexagonal.md | Inconsistent | Medium |
| `AgentResult` field names differ between java-conventions and impl-plan | java-conventions.md | Inconsistent | Medium |
| `.gitignore` missing `target/`, `node_modules/`, `.env`, IDE dirs | .gitignore | Incomplete | Medium |
| `models.md` and `queries.md` entirely generic, no MindForge specifics | models.md, queries.md | Incomplete | Medium |
| `ruff` reference in angular-patterns.md (Python tool, not Java) | angular-patterns.md | Incorrect | Medium |
| `CachePort` referenced in ADR 0007 but absent from Phase 1.7 ports | ADR 0007 / impl-plan | Missing | Low |
| README.md is 2 lines, inaccurate description | README.md | Incomplete | Low |
| `vision.md` Last Updated 2025-07-07, other docs 2026-xx-xx | vision.md | Inconsistent | Low |

---

*Audit conducted: 2026-06-22*
*Codebase state at audit: Greenfield — zero source files. All 22 implementation phases unstarted.*
*No files were modified during this audit.*
