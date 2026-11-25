---
id: P21-06
title: Guard that only MarkdownStructure matches markdown heading or link syntax with a regex
status: ready
plan_tasks: [21.3]
decisions: [D12]
depends_on: [P21-02]
size: M
issue: 71
commit:
fix_rounds: 0
---
## Goal
A source-scan unit test fails the build when a `Pattern.compile` string literal in `src/main/java` contains markdown
heading or escaped link syntax outside `dev.mindforge.domain.model.MarkdownStructure` (plan 21.3 rule 5, T26).
`BundleConformanceValidator` is allowed by exact path with a written reason.

## Context
- Checkstyle (P21-02) runs in `validate`: new test code must pass it (no unused imports, lines <= 120, logger named `log`).
- ArchUnit cannot read string literals, so this rule is a source scan, like
  `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` (exact-path allowlist with
  reasons, `Path.of("src/main/java")`, `@TempDir` planted-offender test). Copy its structure.
- Owner: `src/main/java/dev/mindforge/domain/model/MarkdownStructure.java`.
- Known offenders: `src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java` lines ~35–37,
  `INDEX_ENTRY` (contains `\\]\\(`) and `LOG_DATE` (`"## \\d{4}-..."`).
- Must not be flagged: `Identifier.PATTERN` (`domain/.../Identifier.java:23`, `"[a-z0-9](?:..."`), `DocxParser`
  `"heading ([1-9])"` (~line 46), `ArticleFetcher.URL` (~line 40).
- Put the test with the other 21.3 fitness tests in `src/test/java/dev/mindforge/unit/architecture/` (surefire runs
  `unit/`). Plain JUnit 5 + AssertJ, no Spring.

## Decisions to apply
**D12 — Decision:** enforce the T26 rule with a source-scan guard test (modelled on `OutboundHttpGuardTest`) that scans
`src/main/java` for `Pattern.compile` string literals with markdown heading or link syntax. Exempt the owner
`dev/mindforge/domain/model/MarkdownStructure.java`. Allow `BundleConformanceValidator` by exact path with a written
reason: it validates the OKF bundle file grammar of generated `index.md`/`log.md`, as an independent oracle, not wiki
page syntax. Do not change `BundleConformanceValidator` or `MarkdownStructure`.

**Implementation notes:**
- New test `src/test/java/dev/mindforge/unit/architecture/MarkdownRegexGuardTest.java`. Plain JUnit 5 + AssertJ, no
  Spring context.
- Scan `src/main/java` (`Path.of("src/main/java")`, as OutboundHttpGuardTest does) for string literals passed to
  `Pattern.compile`. Flag a literal when it contains heading syntax (`#` used as a heading marker, for example a
  literal starting `#` or `## `, or `#{`) or escaped link syntax (`\\]\\(` in the Java source, that is `\]\(` in the
  regex).
- Do NOT flag a raw `](` alone: `Identifier.PATTERN` has a character class followed by a group, not link syntax. Do not
  flag `DocxParser` `"heading ([1-9])"` or `ArticleFetcher.URL`.
- `OWNER = "src/main/java/dev/mindforge/domain/model/MarkdownStructure.java"` is exempt.
- `ALLOWED = Map.of("src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java",
  "validates the generated index.md/log.md OKF bundle grammar; an independent oracle, not wiki page syntax")`.
- Tests to cover: (1) the real `src/main/java` has no offender outside OWNER and ALLOWED; (2) each ALLOWED file still
  has at least one hit (no stale entry); (3) a planted offender in a `@TempDir` tree is reported, and OWNER and ALLOWED
  copies in that tree are not (the guard cannot pass vacuously); (4) planted non-markdown literals like the Identifier
  and DocxParser patterns are not reported.
- Add a one-line comment above `INDEX_ENTRY`/`LOG_DATE` in the validator that points to the guard's allowlist
  (comment only; no logic change). Note the exception in the 21.3 plan line or T26 follow-up.
- `BundleConformanceValidatorTest` and the export tests must stay green.
- Risk to watch: the allowlist becomes a habit. Each new entry needs a reason, and a stale entry must fail.

## Acceptance criteria
- [ ] AC1: `MarkdownRegexGuardTest` reports no offender in the real `src/main/java` outside OWNER and ALLOWED.
- [ ] AC2: A test fails when an ALLOWED file has no hit (stale allowlist entry).
- [ ] AC3: A `@TempDir` tree with a planted offender (for example `Pattern.compile("^## (.+)$")` and `Pattern.compile("\\[(.+)\\]\\((.+)\\)")` in a non-owner file) is reported; OWNER and ALLOWED copies in that tree are not.
- [ ] AC4: Planted literals in the shape of `Identifier.PATTERN`, the `DocxParser` heading-style pattern and an URL pattern are not reported.
- [ ] AC5: `BundleConformanceValidator.java` changes only by a one-line comment above `INDEX_ENTRY`/`LOG_DATE` pointing to the guard; `MarkdownStructure.java` is unchanged.
- [ ] AC6: `mvn -B verify -DskipFrontend=true` is green, including `BundleConformanceValidatorTest` and the export tests.

## Test plan
- Unit: `src/test/java/dev/mindforge/unit/architecture/MarkdownRegexGuardTest.java`, four test methods mapping to AC1–AC4, `@TempDir` for AC3/AC4, AssertJ.

## Files likely touched
- `src/test/java/dev/mindforge/unit/architecture/MarkdownRegexGuardTest.java` (new)
- `src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java` (comment only)

## Out of scope
- Moving the validator's patterns into `MarkdownStructure` or widening `MarkdownStructure`'s API (rejected option A).
- Scanning test sources or non-`Pattern.compile` regex uses (`String.matches`, `replaceAll`) unless trivial; if added, record it in Notes.
- Plan text edits (P21-12 writes the "As built" note; record the exception wording in Notes for it).

## Notes
