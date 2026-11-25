---
id: P21-02
title: Enforce the project's stated code-style rules with Checkstyle in the validate phase
status: done
plan_tasks: [21.1]
decisions: [D01, D03]
depends_on: [P21-01]
size: L
issue: 67
commit: cbdfa32
fix_rounds: 0
---
## Goal
`maven-checkstyle-plugin` runs `check` in `validate` with a small project ruleset that encodes only rules the project
already states. Any violation fails `mvn validate` (and so `mvn test`, `package`, `verify`, the factory gate and CI).
The existing code passes: the over-length lines are wrapped and unused imports removed in this same ticket.

## Context
- No Checkstyle in `pom.xml`, no ruleset file. 384 main and 144 test Java files.
- Measured (D01): 0 tabs, 0 trailing whitespace, 0 CRLF, 0 missing final newline, 0 star imports, all main loggers
  named `log`, 0 field `@Autowired` in `src/main`. 17 test files use field `@Autowired` (Spring test idiom, e.g.
  `src/test/java/.../SecurityTest.java:69`). About 125 lines are longer than 120 (46 main, 79 test).
- `.editorconfig`: LF, 4-space indent, trailing-whitespace trim. `.gitattributes`: `* text=auto eol=lf`.
- CI has a Windows leg running `mvn -B package` (`.github/workflows/ci.yml`), which now also runs Checkstyle.
- The fast command `mvn -q -o test ...` is offline: the first online gate run downloads the plugin.
- Rules sources: `docs/standards/backend/coding-style.md` ("enforce", "No Dead Code"), CLAUDE.md (logger `log`,
  constructor injection), `docs/standards/global/conventions.md` § Minimal Dependencies.
- This ticket must leave `mvn -B verify -DskipFrontend=true` green; the line wraps are part of it, not a later ticket.

## Decisions to apply
**D01 — Decision:** add a small project ruleset at `config/checkstyle/checkstyle.xml` that encodes only rules the
project already states (no tabs, no trailing whitespace, LF and final newline, no star/unused/redundant imports, no
field `@Autowired` in main code, `static final Logger` named `log`, line length 120 with an ignore pattern). Run
`maven-checkstyle-plugin` `check` in the `validate` phase with `failOnViolation` true from day one. Fix the current
findings (the over-length lines and any unused imports) in the same ticket. Do not add a suppressions baseline.

**D01 — Implementation notes:**
- `pom.xml`: `org.apache.maven.plugins:maven-checkstyle-plugin` 3.6.x, pinned version, with plugin dependency
  `com.puppycrawl.tools:checkstyle` 10.x (pinned; needed to parse Java 21 records/sealed/pattern switch). Config:
  `configLocation=config/checkstyle/checkstyle.xml`, `suppressionsLocation=config/checkstyle/suppressions.xml`,
  `includeTestSourceDirectory=true`, `consoleOutput=true`, `failOnViolation=true`, `violationSeverity=warning`.
  Phase `validate`, goal `check`.
- `config/checkstyle/checkstyle.xml` (Checker): `NewlineAtEndOfFile`, `FileTabCharacter`, `RegexpSingleline` format
  `\s+$` (trailing whitespace), `LineLength` max=120 ignorePattern `^(package|import) |https?://`. TreeWalker:
  `AvoidStarImport`, `UnusedImports`, `RedundantImport`, a check with `id="AutowiredField"` banning `@Autowired` on
  field definitions (MatchXpath on VARIABLE_DEF in OBJBLOCK with annotation Autowired, or a RegexpMultiline), and
  `RegexpSinglelineJava` format `static\s+final\s+Logger\s+(?!log\b)\w+` message "Logger must be named log".
- `config/checkstyle/suppressions.xml`: one entry only, `<suppress id="AutowiredField" files="[\\/]src[\\/]test[\\/]"/>`.
  No baseline of current findings.
- Fix the lines > 120 (`find src -name '*.java' -exec awk 'length>120{print FILENAME":"FNR}' {} +`) and any
  UnusedImports findings. No other code edits.
- Document the plugin in `docs/project/tech-stack.md` § Linting & Formatting (Checkstyle only; Spotless not adopted).
- Verify: one deliberate violation (e.g. a field `@Autowired` in src/main, a logger named `logger`) fails
  `mvn validate` locally; remove it before commit. A field `@Autowired` in src/test must pass.
- Dissent (pragmatist): if the wraps collide badly with other work, the ticket may fall back to max 140 and record it.

**D03 — Decision:** bind `maven-checkstyle-plugin` goal `check` to the `validate` phase (and SpotBugs to `verify`, P21-03).
CI keeps one `mvn -B verify` step; the lifecycle gives the order Checkstyle -> compile -> tests -> SpotBugs -> JaCoCo
check. Keep the `-Dcheckstyle.skip` escape and document it.

**D03 — Implementation notes (Checkstyle part):**
- Execution id `checkstyle-check`, phase `validate`, goal `check`, `failOnViolation` true. Pin the plugin version.
- Rules must be line-ending neutral (no `NewlineAtEndOfFile` with a fixed `lineSeparator=lf`, no `RegexpMultiline`
  on `\n`), so the Windows leg passes. Use `lineSeparator=lf_cr_crlf`. This overrides D01's "LF" wording: LF itself is
  already enforced by `.gitattributes`/`.editorconfig`. If the `AutowiredField` check is a RegexpMultiline, it must
  not depend on `\n` (prefer MatchXpath).
- Offline: the first online `gate` run downloads the plugin and its dependencies; after that `fast` (`-o`) works. Do
  not change the `fast` command unless Checkstyle makes it measurably slower.
- `docs/project/deployment.md` § Build: state that `mvn test` and `mvn package` also run Checkstyle (validate), and
  document `-Dcheckstyle.skip`.

## Acceptance criteria
- [x] AC1: `pom.xml` declares `maven-checkstyle-plugin` (pinned 3.6.x) with a pinned `com.puppycrawl.tools:checkstyle` 10.x dependency, execution `checkstyle-check` bound to `validate`, goal `check`, with the D01 configuration.
- [x] AC2: `config/checkstyle/checkstyle.xml` contains exactly the D01 checks (NewlineAtEndOfFile with `lf_cr_crlf`, FileTabCharacter, trailing-whitespace RegexpSingleline, LineLength 120 with the ignore pattern, AvoidStarImport, UnusedImports, RedundantImport, `AutowiredField`, logger-name check) and nothing more.
- [x] AC3: `config/checkstyle/suppressions.xml` has only the `AutowiredField` suppression for `src/test`.
- [x] AC4: `mvn -B verify -DskipFrontend=true` is green with no Checkstyle violation; no line > 120 remains except those matching the ignore pattern; unused imports removed. Changes to Java files are formatting/import-only (no behaviour change).
- [x] AC5: A planted field `@Autowired` in `src/main`, and separately a `static final Logger logger`, each make `mvn -B validate` fail; a field `@Autowired` in `src/test` passes. Verified by hand, not committed (record in Notes).
- [x] AC6: `mvn -B validate -Dcheckstyle.skip` skips the check.
- [x] AC7: `docs/project/tech-stack.md` § Linting & Formatting names Checkstyle (Spotless not adopted) and why; `docs/project/deployment.md` § Build states that `test`/`package` run Checkstyle and documents `-Dcheckstyle.skip`.

## Test plan
- The build itself is the test: `mvn -B verify -DskipFrontend=true` (AC4). AC5/AC6 by manual runs recorded in Notes.
- Existing unit and integration suites prove the wraps changed no behaviour.

## Files likely touched
- `pom.xml`
- `config/checkstyle/checkstyle.xml` (new)
- `config/checkstyle/suppressions.xml` (new)
- About 125 Java files under `src/main/java` and `src/test/java` (line wraps, unused imports)
- `docs/project/tech-stack.md`, `docs/project/deployment.md`

## Out of scope
- Google/Sun style checks, Javadoc, naming or indentation rules, Spotless, a suppressions baseline.
- SpotBugs (P21-03) and CI workflow edits (P21-07).
- Any refactor beyond wrapping and removing unused imports.

## Notes
- Decided: pinned maven-checkstyle-plugin 3.6.0 (only 3.6.x release) and Checkstyle 10.26.1 (newest 10.x), as pom properties, because the ticket asks for pinned 3.6.x / 10.x.
- Decided: checkstyle.xml has no SuppressionFilter module; the plugin's `suppressionsLocation` adds it, because AC2 allows only the D01 checks.
- Decided: `AutowiredField` is a MatchXpath on `//OBJBLOCK/VARIABLE_DEF[./MODIFIERS/ANNOTATION/IDENT[@text='Autowired']]`, because D03 prefers MatchXpath (line-ending neutral).
- Decided: string literals and text blocks were split so the value stays the same (`+` concatenation; `\` line continuation in LogRendererTest), because AC4 allows no behaviour change. Max stays 120; no 140 fallback needed.
- AC5 verified by hand (2026-11-25): a field `@Autowired` in src/main failed `mvn -B validate` with [AutowiredField]; a `static final Logger logger` in src/main failed it with "Logger must be named log"; a field `@Autowired` in src/test passed. The planted files were deleted.
- AC6 verified by hand: `mvn -B validate -Dcheckstyle.skip` passed with a planted logger violation present.
- AC4: `mvn -B verify -DskipFrontend=true` BUILD SUCCESS (879 unit, 145 integration tests). One unused import removed (AiConfig `java.time.Duration`).
- Tester R1 AC map: AC1 pom.xml diff (checkstyle-check in validate, gate log shows it ran); AC2 checkstyle.xml reviewed; AC3 suppressions.xml reviewed; AC4 gate `mvn -B verify -DskipFrontend=true` BUILD SUCCESS (145 IT) + awk finds 0 lines >120 outside ignore pattern; AC5/AC6 implementer's manual runs in Notes (not re-run); AC7 tech-stack.md:167, deployment.md:20-22.
