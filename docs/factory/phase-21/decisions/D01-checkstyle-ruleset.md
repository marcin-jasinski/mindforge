---
id: D01
question: Q01
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q01.r1.tally.md]
adr:
---
## Decision
Use A: add a small project ruleset at `config/checkstyle/checkstyle.xml` that encodes only rules the project already states (no tabs, no trailing whitespace, LF and final newline, no star/unused/redundant imports, no field `@Autowired` in main code, `static final Logger` named `log`, line length 120 with an ignore pattern). Run `maven-checkstyle-plugin` `check` in the `validate` phase with `failOnViolation` true from day one. Fix the current findings (the over-length lines and any unused imports) in the same ticket. Do not add a suppressions baseline.

## Why
- Measured on all 528 .java files: 0 tabs, 0 trailing whitespace, 0 CRLF, 0 missing final newline, 0 star imports, all main loggers named `log`, 0 field `@Autowired` in src/main. So the ruleset gates with near-zero churn.
- Plan 21.1 (implementation-plan.md:1782) puts Checkstyle as a CI gate stage; coding-style.md:6-7 says "enforce". Report-only (C) does not satisfy this.
- Every rule traces to .editorconfig, coding-style.md ("No Dead Code"), or CLAUDE.md (logger `log`, constructor injection). B imports a style the project never adopted.
- LineLength: 2 of 3 ballots keep 120; the option as asked includes it; coding-style.md asks to enforce "line breaks". The cost is 125 mechanical wraps (46 main, 79 test), with low risk. So keep 120.
- 17 test files use field `@Autowired` (Spring test idiom, e.g. SecurityTest.java:69); the CLAUDE.md rule concerns beans. Scope that one check to src/main.

## Rejected
- B: Google checks bring Javadoc/naming/indent rules no standard states, and a suppressions baseline freezes debt (speculative configuration).
- C: report-only enforces nothing; contradicts plan 21.1 and coding-style.md.
- No LineLength (pragmatist's preference): coding-style.md asks to enforce line breaks, and 120 costs only mechanical wraps.

## Dissent
Pragmatist: no written standard states a line limit, so LineLength is a new rule; prefer to omit it or use 140 (13 findings). Risk to watch: wrapping 125 lines can cause merge churn with other phase-21 tickets. Do the wraps in one early commit; if they collide, the ticket may fall back to max 140 and record it.

## Implementation notes
- `pom.xml`: `org.apache.maven.plugins:maven-checkstyle-plugin` 3.6.x, pinned version, with plugin dependency `com.puppycrawl.tools:checkstyle` 10.x (pinned; needed to parse Java 21 records/sealed/pattern switch). Config: `configLocation=config/checkstyle/checkstyle.xml`, `suppressionsLocation=config/checkstyle/suppressions.xml`, `includeTestSourceDirectory=true`, `consoleOutput=true`, `failOnViolation=true`, `violationSeverity=warning`. Execution id `checkstyle`, phase `validate`, goal `check`, so the gate `mvn -B verify -DskipFrontend=true` runs it.
- `config/checkstyle/checkstyle.xml` (Checker): `NewlineAtEndOfFile` (lineSeparator=lf), `FileTabCharacter`, `RegexpSingleline` format `\s+$` (trailing whitespace), `LineLength` max=120 ignorePattern `^(package|import) |https?://`. TreeWalker: `AvoidStarImport`, `UnusedImports`, `RedundantImport`, a check with `id="AutowiredField"` banning `@Autowired` on field definitions (MatchXpath on VARIABLE_DEF in OBJBLOCK with annotation Autowired, or a RegexpMultiline), and `RegexpSinglelineJava` format `static\s+final\s+Logger\s+(?!log\b)\w+` message "Logger must be named log".
- `config/checkstyle/suppressions.xml`: one entry only, `<suppress id="AutowiredField" files="[\\/]src[\\/]test[\\/]"/>`. No baseline of current findings.
- Fix the 125 lines > 120 (`find src -name '*.java' -exec awk 'length>120{print FILENAME":"FNR}' {} +`) and any UnusedImports findings. No other code edits.
- Document the plugin in `docs/project/tech-stack.md` § Linting & Formatting (Checkstyle only; Spotless not adopted) per conventions.md Minimal Dependencies.
- CI (21.1): run `mvn -B checkstyle:check` first, before SpotBugs.
- Verify: one deliberate violation (e.g. a field `@Autowired` in src/main, a logger named `logger`) fails `mvn validate` locally; remove it before commit. A field `@Autowired` in src/test must pass.
