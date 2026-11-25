---
id: D02
question: Q02
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q02.r1.tally.md]
adr:
---
## Decision
Use A. Add `spotbugs-maven-plugin` (SpotBugs engine 4.8 or later) to `pom.xml` with `threshold=Medium`, `effort=Max` and `failOnError=true`. Bind the `check` goal to `verify`. Fail the build on every finding that the exclude filter does not drop. Create `config/spotbugs-exclude.xml`. It drops `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` and `CT_CONSTRUCTOR_THROW` for the full project, and each match has an XML comment that gives the reason. Fix all other findings in code. Do not add FindSecBugs in this phase. Put it on the follow-up list.

## Why
- Plan 21.1 (implementation-plan.md:1782) puts SpotBugs as a real CI gate. It names only SpotBugs.
- The excluded families are false positives for this design: records with `List` fields, beans held through constructor injection, and validating constructors. The filter drops them without a change to behaviour, as the question constraints require.
- A High-only threshold (B) does not find most real defects, for example null dereferences and resource leaks, which are often Medium.
- FindSecBugs (C) is a new dependency that the plan does not name (conventions.md:15). It can also add noise that turns the gate red. Phase 20 tests and the spec-security review lens already cover the security rules.
- No ADR covers static analysis. This is a build-time plugin, not a runtime dependency, so the class is local.

## Rejected
- B: the gate is too weak. It passes most Medium-confidence real defects.
- C: an extra dependency that the plan does not name, with a risk of a noisy, red gate. Deferred, not refused.

## Dissent
None. All three ballots have the same risk: Medium may show many findings outside the three families. Watch the first run. Do not change behaviour to satisfy a false positive. Use a narrow exclusion (class or method plus pattern) with a reason.

## Implementation notes
- `pom.xml`: `com.github.spotbugs:spotbugs-maven-plugin` at a pinned current version (engine 4.8+ for Java 21 bytecode). Configuration: `<threshold>Medium</threshold>`, `<effort>Max</effort>`, `<failOnError>true</failOnError>`, `<excludeFilterFile>${project.basedir}/config/spotbugs-exclude.xml</excludeFilterFile>`. Execution: goal `check`, phase `verify`. Analyse main classes only (not test classes).
- `config/spotbugs-exclude.xml`: one `<Match><Bug pattern="..."/></Match>` each for `EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, `CT_CONSTRUCTOR_THROW`, and an XML comment before each one with the reason.
- Run `mvn -B verify -DskipFrontend=true` once. Triage each remaining finding: fix real defects. For a false positive, add a narrow `<Match>` (class or method plus pattern) with a reason. Add no other project-wide pattern exclusion.
- If the build generates code (for example MapStruct or Hibernate metamodel classes) that gives findings, exclude it by package with a reason.
- Document the plugin and the reason for it in the build or tech-stack docs (Minimal Dependencies).
- Add FindSecBugs as a follow-up item with the note "deferred by D02".
- The factory gate (`mvn -B verify`) must be green after the change.
