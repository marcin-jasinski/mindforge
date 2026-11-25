---
id: P21-05
title: Enforce thin controllers, independent model services and RunWorker-only queue reads with ArchUnit
status: done
plan_tasks: [21.3]
decisions: [D11]
depends_on: [P21-04]
size: S
issue: 70
commit: 25a6bb8
fix_rounds: 0
---
## Goal
Three more ArchUnit rules fail the build when: a `@RestController` depends on a port or persistence type (plan 21.3
rule 2); a `dev.mindforge.agent` class depends on another `dev.mindforge.agent` class (rule 3); a class other than
`RunWorker` calls `IngestRunRepository.findUnfinished` or `knowledgeBasesWithQueuedRuns` (rule 4, T25).

## Context
- Checkstyle (P21-02) runs in `validate`: new test code must pass it (no unused imports, lines <= 120, logger named `log`).
- P21-04 adds `archunit-junit5` and `src/test/java/dev/mindforge/unit/architecture/ArchitectureTest.java`. Add these
  rules there (or in a sibling class in the same package, same import options: `dev.mindforge`, tests excluded).
- Controllers live in `src/main/java/dev/mindforge/api/controller/`. `AuthController` holds Spring Security's
  `org.springframework.security.oauth2.client.registration.ClientRegistrationRepository`; this is third-party and must
  pass with no exception. `GlobalExceptionHandler` is `@RestControllerAdvice`, not selected.
- Model services: `src/main/java/dev/mindforge/agent/`. Today no agent class references another agent class.
  Classes inside one agent class (nested/inner, lambdas) are the same class and must not be flagged — compare
  top-level owner, or exclude dependencies on itself/its nested classes.
- T25: `src/main/java/dev/mindforge/domain/port/IngestRunRepository.java:80,82` declares `findUnfinished()` and
  `knowledgeBasesWithQueuedRuns()`. The only callers are
  `src/main/java/dev/mindforge/application/service/RunWorker.java:106,116`. The adapter
  `infrastructure/persistence/adapter/IngestRunRepositoryAdapter.java` implements them (implementing is not calling).
  See `docs/wayfinder/tickets/25-port-read-surface.md`.

## Decisions to apply
**D11 — Decision:** a "repository" for plan 21.3 rule 2 is any type in `dev.mindforge.domain.port..` (repositories,
stores, query ports and all other ports) or in `dev.mindforge.infrastructure.persistence..` (adapters, JPA
repositories, entities, mappers). No `@RestController` class in any package may depend on such a type; controllers
reach data only through `dev.mindforge.application` services. Third-party types outside `dev.mindforge` (for example
Spring Security's `ClientRegistrationRepository` in `AuthController`) are not covered and need no exception.

**Implementation notes:**
- ArchUnit test (no Spring context): `noClasses().that().areAnnotatedWith(RestController.class).should()
  .dependOnClassesThat().resideInAnyPackage("dev.mindforge.domain.port..", "dev.mindforge.infrastructure.persistence..")`.
- Import the whole `dev.mindforge` package so controllers in every adapter package are checked; select by the
  annotation `org.springframework.web.bind.annotation.RestController`, not by package. `@RestControllerAdvice`
  (`GlobalExceptionHandler`) is not selected; Discord/Slack command classes are not controllers.
- Do not add an exception for `ClientRegistrationRepository`; the test must pass with `AuthController` as it is.
- Out of scope: controller imports of `infrastructure.event`/`security`/`export` types (`SseProgressNotifier`,
  `JwtService`, `BundleExporter`); do not forbid them in this rule.
- Name the rule clearly, e.g. `controllers_do_not_access_ports_or_persistence_directly`, and add `.because(...)` citing
  the thin-controller rule.
- Risk to watch: a future thin read endpoint must add a pass-through application-service method instead of reading a
  query port directly.

## Acceptance criteria
- [x] AC1: Rule `controllers_do_not_access_ports_or_persistence_directly` exists exactly as D11 defines it and passes on current code with `AuthController` unchanged.
- [x] AC2: Rule `model_services_do_not_depend_on_other_model_services`: no class in `dev.mindforge.agent..` depends on a different top-level class in `dev.mindforge.agent..`; it passes on current code. Shared non-service types, if any exist in `agent` today and make the rule fail, are recorded in Notes and the ticket stops for review (no silent allowlist).
- [x] AC3: Rule `only_run_worker_reads_the_run_queue`: no class other than `dev.mindforge.application.service.RunWorker` calls `IngestRunRepository.findUnfinished()` or `IngestRunRepository.knowledgeBasesWithQueuedRuns()`; `.because(...)` cites T25. It passes on current code.
- [x] AC4: Each new rule is shown able to fail by a planted violation (for example a controller field of a `domain.port` type; an agent class referencing another; a second caller of `findUnfinished`), verified by hand and removed before commit (record in Notes).
- [x] AC5: `mvn -B verify -DskipFrontend=true` is green.

## Test plan
- Unit: `unit/architecture/ArchitectureTest` (ArchUnit `@ArchTest` fields), run by surefire. AC4 by manual planted violations.

## Files likely touched
- `src/test/java/dev/mindforge/unit/architecture/ArchitectureTest.java`

## Out of scope
- The markdown-regex rule (P21-06).
- Forbidding controller use of `infrastructure.event`/`security`/`export` types.
- Any production change.

## Notes
- Decided: rule 3 uses a small custom `ArchCondition` that compares top-level owners (via `getEnclosingClass`, array base types) because ArchUnit has no built-in "other top-level class in the same package tree" predicate; no shared non-service types exist in `agent`, so no allowlist.
- AC4 verified by hand: planted `api.controller.PlantedController` (field of `IngestRunRepository`), `agent.PlantedAgent` (field of `PageWriter`) and `application.service.PlantedCaller` (calls `findUnfinished` and `knowledgeBasesWithQueuedRuns`); `ArchitectureTest` reported 3 failures, one per new rule (4 violations). Files and their compiled classes removed.

### Deferred findings
- correctness/C1 minor 0.6 src/test/java/dev/mindforge/unit/architecture/ArchitectureTest.java:86: `callMethod(IngestRunRepository.class, "findUnfinished")` matches only call sites whose bytecode owner is exactly `IngestRunRepository`, so a call through the concrete `IngestRunRepositoryAdapter` type (or a sub-interface) is not caught.
- spec-security/S1 minor 0.5 src/test/java/dev/mindforge/unit/architecture/ArchitectureTest.java:86: Rule `only_run_worker_reads_the_run_queue` matches only calls whose owner is `IngestRunRepository`; a call through the concrete `IngestRunRepositoryAdapter` type (for example from another infrastructure class that injects the adapter) does not match.
