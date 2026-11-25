---
id: P21-03
title: Fail verify on SpotBugs findings at Medium threshold and fix the real defects
status: done
plan_tasks: [21.1]
decisions: [D02, D03]
depends_on: [P21-02]
size: L
issue: 68
commit: 93430bf
fix_rounds: 0
---
## Goal
`spotbugs-maven-plugin` runs `check` in `verify` (before the JaCoCo check) on main classes with threshold Medium and
effort Max. Any finding not dropped by `config/spotbugs-exclude.xml` fails the build. All current real findings are
fixed in this ticket, so `mvn -B verify -DskipFrontend=true` stays green.

## Context
- No SpotBugs in `pom.xml`. JaCoCo: `jacoco-maven-plugin` 0.8.13, `prepare-agent` + `check` (BUNDLE / INSTRUCTION /
  COVEREDRATIO 0.70) bound in `verify` (`pom.xml` ~436–454).
- P21-02 has added Checkstyle in `validate`; code changes here must pass it.
- Design facts behind the excludes: records with `List` fields, beans held through constructor injection, validating
  constructors.
- Error-handling standard (`docs/standards/global/error-handling.md`) and CLAUDE.md rules apply to every code fix:
  no bare `RuntimeException`, constructor injection, hexagonal imports (the ArchUnit tests of P21-04/05 will enforce).
- `docs/standards/global/conventions.md` § Minimal Dependencies: document why the plugin is included.

## Decisions to apply
**D02 — Decision:** Add `spotbugs-maven-plugin` (SpotBugs engine 4.8 or later) to `pom.xml` with `threshold=Medium`,
`effort=Max` and `failOnError=true`. Bind the `check` goal to `verify`. Fail the build on every finding that the
exclude filter does not drop. Create `config/spotbugs-exclude.xml`. It drops `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` and
`CT_CONSTRUCTOR_THROW` for the full project, and each match has an XML comment that gives the reason. Fix all other
findings in code. Do not add FindSecBugs in this phase. Put it on the follow-up list.

**D02 — Implementation notes:**
- `pom.xml`: `com.github.spotbugs:spotbugs-maven-plugin` at a pinned current version (engine 4.8+ for Java 21
  bytecode). Configuration: `<threshold>Medium</threshold>`, `<effort>Max</effort>`, `<failOnError>true</failOnError>`,
  `<excludeFilterFile>${project.basedir}/config/spotbugs-exclude.xml</excludeFilterFile>`. Execution: goal `check`,
  phase `verify`. Analyse main classes only (not test classes).
- `config/spotbugs-exclude.xml`: one `<Match><Bug pattern="..."/></Match>` each for `EI_EXPOSE_REP`, `EI_EXPOSE_REP2`,
  `CT_CONSTRUCTOR_THROW`, and an XML comment before each one with the reason.
- Run `mvn -B verify -DskipFrontend=true` once. Triage each remaining finding: fix real defects. For a false positive,
  add a narrow `<Match>` (class or method plus pattern) with a reason. Add no other project-wide pattern exclusion.
- If the build generates code (for example MapStruct or Hibernate metamodel classes) that gives findings, exclude it by
  package with a reason.
- Document the plugin and the reason for it in the build or tech-stack docs (Minimal Dependencies).
- Add FindSecBugs as a follow-up item with the note "deferred by D02".
- Do not change behaviour to satisfy a false positive.

**D03 — Decision (SpotBugs part):** bind `spotbugs-maven-plugin` goal `check` to the `verify` phase. CI keeps one
`mvn -B verify` step; the lifecycle order is Checkstyle -> compile -> tests -> SpotBugs -> JaCoCo check. Keep the
`-Dspotbugs.skip` escape and document it.

**D03 — Implementation notes (SpotBugs part):**
- Execution id `spotbugs-check`, phase `verify`, goal `check`, `failOnError` true. Declare SpotBugs above
  `jacoco-maven-plugin` in the POM so that in `verify` SpotBugs runs before `jacoco-check` (same-phase executions run in
  POM order). Pin the plugin version.
- A SpotBugs exclude file gives one reason per exclusion.
- `docs/project/deployment.md` § Build: state that `mvn verify` also runs SpotBugs before the coverage gate, and
  document `-Dspotbugs.skip`.

## Acceptance criteria
- [x] AC1: `pom.xml` declares `spotbugs-maven-plugin` (pinned, engine 4.8+), execution `spotbugs-check` in `verify`, Medium/Max/failOnError, main classes only, declared before `jacoco-maven-plugin`; the `verify` log shows `spotbugs-check` before `jacoco-check`.
- [x] AC2: `config/spotbugs-exclude.xml` has the three project-wide pattern matches, each preceded by a reason comment, plus only narrow class/method matches with reasons for triaged false positives.
- [x] AC3: Every other finding is fixed in code; each fix keeps existing tests green and changes no observable behaviour except removing the defect. A fix that changes behaviour (for example a null-dereference path) has a unit test that proves the new behaviour.
- [x] AC4: `mvn -B verify -DskipFrontend=true` is green, including the JaCoCo 70% check.
- [x] AC5: `mvn -B verify -DskipFrontend=true -Dspotbugs.skip` skips SpotBugs.
- [x] AC6: Docs: `deployment.md` § Build (SpotBugs in `verify`, `-Dspotbugs.skip`); `tech-stack.md` names SpotBugs and why; FindSecBugs is listed as a follow-up "deferred by D02" (in the plan Phase 21 section or roadmap follow-ups).

## Test plan
- Build-level: `mvn -B verify -DskipFrontend=true` (AC1, AC4); manual skip run (AC5).
- Unit tests in `src/test/java/dev/mindforge/unit/<layer>/...` for any defect fix that changes behaviour (AC3), Mockito/AssertJ, `make*` factories.

## Files likely touched
- `pom.xml`
- `config/spotbugs-exclude.xml` (new)
- Main Java files with real findings (unknown until the first run; list them in Notes)
- `docs/project/deployment.md`, `docs/project/tech-stack.md`, `docs/project/implementation-plan.md` or `docs/project/roadmap.md` (follow-up line)

## Out of scope
- FindSecBugs or any other SpotBugs plugin.
- Analysing test classes.
- Behaviour changes made only to silence a false positive; broad package-level excludes of hand-written code.
- CI workflow edits (P21-07).

## Notes
- Decided: pin `spotbugs-maven-plugin` 4.9.8.5 (engine 4.9.8), not 4.10.5.0, because the 4.10 line needs Maven 3.8.9 and the build host has Maven 3.8.7; 4.9.8 meets the 4.8+ requirement.
- Findings of the first run (3): `PipelineCommandRunner.mimeTypeOf` and `ingest` NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (`Path.getFileName()` is null for a root path) fixed in code; `CliExitOnReady.onApplicationEvent` DM_EXIT is a false positive (ending the JVM is the listener's purpose), dropped by a class+method match.
- Decided: a root path in `ingest` is refused with the existing "cannot read file" message before the read, because the read of a root already gave that refusal (no visible change).
- Tester AC map: AC1 -> verify log order spotbugs-check before jacoco-check (seen); AC2 -> config/spotbugs-exclude.xml inspected; AC3 -> MimeTypeOfTest.aPathWithNoFileNameIsOctetStream, PipelineCommandRunnerTest.aRootPathWithNoFileNameIsRefusedWithoutIngest (ADDED); AC4 -> full gate green; AC5 -> run with -Dspotbugs.skip prints "Spotbugs plugin skipped"; AC6 -> docs diff inspected.
