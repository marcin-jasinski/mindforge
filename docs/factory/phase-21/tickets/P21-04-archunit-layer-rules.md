---
id: P21-04
title: Add ArchUnit and enforce the hexagonal layer table as fitness functions
status: done
plan_tasks: [21.3]
decisions: []
depends_on: [P21-02]
size: S
issue: 69
commit: ab04617
fix_rounds: 0
---
## Goal
A unit test `ArchitectureTest` (ArchUnit, no Spring context) fails the build when a class breaks the layer table of
`CLAUDE.md` / `docs/standards/architecture/hexagonal.md`. It covers plan 21.3 rule 1 ("no domain class imports from
`infrastructure` or `api`") in its stronger form (domain JDK only), plus the application and agent allowlists and
"nothing inward depends on an adapter".

## Context
- Checkstyle (P21-02) runs in `validate`: new test code must pass it (no unused imports, lines <= 120, logger named `log`).
- No ArchUnit dependency or test exists. Existing source-scan guards set the house style and stay as they are:
  `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` (ADR 0023),
  `unit/infrastructure/export/ExportReadsNoStudyDataGuardTest.java`, `unit/slack/SlackAdapterBoundaryTest.java`.
- Surefire runs everything except `**/integration/**` and `**/e2e/**` (`pom.xml` ~312–317). Put the test under
  `src/test/java/dev/mindforge/unit/architecture/` so surefire runs it.
- Layer table (CLAUDE.md § Architecture; hexagonal.md):
  - `dev.mindforge.domain..` → JDK only (`java..`) and `dev.mindforge.domain..`.
  - `dev.mindforge.application..` → `java..`, `dev.mindforge.domain..`, `dev.mindforge.application..`,
    `dev.mindforge.agent..`, `org.springframework.transaction..`, `org.slf4j..`.
  - `dev.mindforge.agent..` → `java..`, `dev.mindforge.domain..`, `dev.mindforge.agent..`,
    `dev.mindforge.infrastructure.ai..` (plus the third-party types that `infrastructure.ai` exposes only if the code
    already uses them — check by running the rule; record any added allowance with a reason in Notes).
  - Adapters `dev.mindforge.api..`, `dev.mindforge.cli..`, `dev.mindforge.discord..`, `dev.mindforge.slack..`:
    no class in domain, application, agent or infrastructure depends on them.
  - `dev.mindforge.MindForgeApplication` (root package) is allowed to depend on anything.
- Scope says a grep of current imports shows the code already meets the full table, so no production change is expected.
- Testing rules (CLAUDE.md § Testing): no Spring context in unit tests; AssertJ for any non-ArchUnit assertion.

## Decisions to apply
No D-decision. Answered questions that bind this ticket:
- **Q09 (answered by plan 21.3 and ADR 0023 § Considered Options):** Add `com.tngtech.archunit:archunit-junit5`
  (test scope) for 21.3; keep `OutboundHttpGuardTest` as it is. Keep the existing source-scan guards; do not migrate
  them. Document the dependency's purpose (conventions.md § Minimal Dependencies).
- **Q10 (answered by CLAUDE.md and hexagonal.md layer table):** Encode the full table (domain JDK-only, application and
  agent allowlists, nothing inward depends on an adapter). The domain rule is "JDK only" (stronger than the plan's
  "not infrastructure or api"). `MindForgeApplication` in the root package is allowed.

## Acceptance criteria
- [x] AC1: `pom.xml` has `com.tngtech.archunit:archunit-junit5` in test scope with a pinned version property (1.3.x or later, supports Java 21 bytecode).
- [x] AC2: `src/test/java/dev/mindforge/unit/architecture/ArchitectureTest.java` imports `dev.mindforge` main classes only (`ImportOption.DoNotIncludeTests`) and declares named `@ArchTest` rules, each with `.because(...)` citing CLAUDE.md / hexagonal.md: `domain_depends_only_on_the_jdk`, `application_depends_only_on_domain_agent_tx_and_slf4j`, `model_services_depend_only_on_domain_and_infrastructure_ai`, `inner_layers_do_not_depend_on_adapters`.
- [x] AC3: All rules pass against the current code with no production change.
- [x] AC4: Each rule is proven able to fail: a deliberate violation (for example a `dev.mindforge.domain` class importing `org.springframework.*`) turns the test red; verified by hand and removed before commit (record in Notes).
- [x] AC5: The dependency and its purpose are listed in `docs/project/tech-stack.md` (test tooling) per Minimal Dependencies.
- [x] AC6: `mvn -B verify -DskipFrontend=true` is green.

## Test plan
- Unit: `unit/architecture/ArchitectureTest` (ArchUnit JUnit 5, `@AnalyzeClasses(packages = "dev.mindforge", importOptions = DoNotIncludeTests.class)`), run by surefire. AC4 by a manual planted violation.

## Files likely touched
- `pom.xml`
- `src/test/java/dev/mindforge/unit/architecture/ArchitectureTest.java` (new)
- `docs/project/tech-stack.md`

## Out of scope
- The controller, agent-to-agent and T25 rules (P21-05) and the markdown-regex guard (P21-06).
- Migrating `OutboundHttpGuardTest` or other source-scan guards to ArchUnit.
- Any production refactor; if a rule fails on real code, stop and record it in Notes rather than weaken the rule silently.

## Notes
- Decided: ArchUnit version 1.4.1 (`archunit.version`) because it is the newest 1.x release in reach and reads Java 21 bytecode.
- Decided: the agent rule needs no added allowance because `dev.mindforge.agent` imports nothing outside java, domain, agent and infrastructure.ai (the rule passes as written).
- AC4 verified by hand: planted `ZzPlantedViolation` in domain, application, agent (field of type `org.springframework.stereotype.Component`) and infrastructure (field of type `api.controller.AuthController`); all 4 rules went red (Tests run: 4, Failures: 4); files removed.
- Tester r1 AC map: AC1 pom.xml archunit.version 1.4.1 test scope; AC2/AC3 ArchitectureTest 4 named @ArchTest rules, pass in gate; AC4 Notes manual plant (4 red); AC5 tech-stack.md two entries; AC6 `mvn -B verify -DskipFrontend=true` BUILD SUCCESS.
