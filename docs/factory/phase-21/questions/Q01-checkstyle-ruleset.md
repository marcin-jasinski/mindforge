---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W05, W07]
---
## Question
Which Checkstyle ruleset does the build enforce, and do violations fail the build from day one?

## Context
Plan 21.1 puts Checkstyle first in CI. `pom.xml` has no `maven-checkstyle-plugin` and the repo has no ruleset file.
The codebase has 384 main and 144 test Java files written without Checkstyle. A stock ruleset (Google or Sun checks)
will report hundreds or thousands of findings (Google checks expect 2-space indent; `.editorconfig` sets 4; Sun checks
demand Javadoc on everything and an 80-column limit). The choice decides how much existing code the phase touches and
whether the check blocks PRs. `docs/standards/global/coding-style.md` § Automatic Formatting asks for automated
enforcement but names no ruleset. `docs/project/tech-stack.md` § Development Tools says "Checkstyle / Spotless".

## Known options
- A: A small project ruleset (`config/checkstyle.xml`) that encodes rules the standards already state and the code
  already meets: no tabs, no trailing whitespace, LF/newline at EOF, no unused or star imports, no `@Autowired` on
  fields, logger named `log`, line length matching the existing code (120). `failOnViolation` true. Fix the few findings.
- B: Google checks with a 4-space override and a suppressions file for current findings; fail on new violations.
- C: A stock ruleset in report-only mode (`failOnViolation` false); CI publishes the report but does not fail.

## Constraints
- `docs/standards/global/minimal-implementation.md`: no speculative configuration.
- `docs/standards/global/conventions.md` § Minimal Dependencies (plugin choice documented).
- CLAUDE.md Java Conventions: logger named `log`; constructor injection, never field `@Autowired`.
- The factory gate (`mvn -B verify -DskipFrontend=true`) must stay green at the end of the phase.
