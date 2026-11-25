---
id: D12
question: Q12
status: decided
method: unanimous
class: local
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q12.r1.tally.md]
adr:
---
## Decision
Use B: enforce the T26 rule with a source-scan guard test (modelled on `OutboundHttpGuardTest`) that scans
`src/main/java` for `Pattern.compile` string literals with markdown heading or link syntax. Exempt the owner
`dev/mindforge/domain/model/MarkdownStructure.java`. Allow `BundleConformanceValidator` by exact path with a written
reason: it validates the OKF bundle file grammar of generated `index.md`/`log.md`, as an independent oracle, not wiki
page syntax. Do not change `BundleConformanceValidator` or `MarkdownStructure`.

## Why
- The validator's `INDEX_ENTRY` and `LOG_DATE` (BundleConformanceValidator.java:35-37) check a whole-line bundle
  grammar (OKF §9, javadoc line 19). That is not page-body heading/section/link meaning.
- The validator is the oracle for "every exported bundle passes". If it parsed with the same `MarkdownStructure` that
  the producers use, one parser bug would pass both sides (option A).
- `MarkdownStructure` exposes only level-1 sections and inline links (MarkdownStructure.java:63-101). Option A would
  widen a JDK-only domain API for one consumer.
- Option C leaves future export or infrastructure regexes unguarded. B keeps the rule global with one named exception.
- ArchUnit cannot see `Pattern.compile` literals; the exact-path allowlist source scan already exists in
  OutboundHttpGuardTest.java:24,33.

## Rejected
- A: loses oracle independence and adds bundle-file concepts to the domain parser for one consumer.
- C: a silent hole for any new regex outside domain/application/agent.

## Dissent
None. Risk to watch: the allowlist becomes a habit. Each new entry needs a reason, and a stale entry must fail.

## Implementation notes
- New test, for example `src/test/java/dev/mindforge/unit/architecture/MarkdownRegexGuardTest.java` (put it with the
  other 21.3 fitness tests). Plain JUnit 5 + AssertJ, no Spring context.
- Scan `src/main/java` (`Path.of("src/main/java")`, as OutboundHttpGuardTest does) for string literals passed to
  `Pattern.compile`. Flag a literal when it contains heading syntax (`#` used as a heading marker, for example a
  literal starting `#` or `## `, or `#{`) or escaped link syntax (`\\]\\(` in the Java source, that is `\]\(` in the
  regex).
- Do NOT flag a raw `](` alone: `Identifier.PATTERN` (Identifier.java:23, `"[a-z0-9](?:..."`) has a character class
  followed by a group, not link syntax. Do not flag `DocxParser` `"heading ([1-9])"` (DocxParser.java:46) or
  `ArticleFetcher.URL` (ArticleFetcher.java:40).
- `OWNER = "src/main/java/dev/mindforge/domain/model/MarkdownStructure.java"` is exempt.
- `ALLOWED = Map.of("src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java",
  "validates the generated index.md/log.md OKF bundle grammar; an independent oracle, not wiki page syntax")`.
- Tests to cover: (1) the real `src/main/java` has no offender outside OWNER and ALLOWED; (2) each ALLOWED file still
  has at least one hit (no stale entry); (3) a planted offender in a `@TempDir` tree is reported, and OWNER and ALLOWED
  copies in that tree are not (the guard cannot pass vacuously); (4) planted non-markdown literals like the Identifier
  and DocxParser patterns are not reported.
- Add a one-line comment above `INDEX_ENTRY`/`LOG_DATE` in the validator that points to the guard's allowlist
  (comment only; no logic change). Note the exception in the 21.3 plan line or T26 follow-up.
- `BundleConformanceValidatorTest` and the export tests must stay green (no production change, so they should).
