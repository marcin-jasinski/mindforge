---
id: Q12
status: decided
decision: D12
impact: medium
blocks: [W04]
---
## Question
How is the T26 rule ("no class outside `MarkdownStructure` matches heading or link syntax with a regex") enforced, and
what happens to `BundleConformanceValidator`, which breaks it today?

## Context
Plan 21.3: "reviewed by a name rule on `Pattern` constants mentioning `#` or `](`". T26
(`docs/wayfinder/tickets/26-markdown-and-link-rules.md` § Answer): "One pure parser, `MarkdownStructure` ... Every
consumer uses it, and nobody writes a second regex." ArchUnit sees classes, fields and calls, not the string passed to
`Pattern.compile`, so the literal check needs a source scan (as `OutboundHttpGuardTest` does) or a reflective read of
`static final Pattern` field values.
Offenders today, `src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java`:
- `INDEX_ENTRY = "\\* \\[(?:\\\\.|[^\\\\\\]\\n])*\\]\\([^()\\s]+\\)(?: - .*)?"` — link syntax in `index.md` lines;
- `LOG_DATE = "## \\d{4}-\\d{2}-\\d{2}"` — a level-2 heading in `log.md`.
These validate the OKF bundle grammar of the generated `index.md`/`log.md` files (plan 9b.2), not page bodies.
`MarkdownStructure` treats only level-1 headings as sections and parses inline links; it may not expose what the
validator needs (a whole-line index-entry shape, `##` headings).

## Known options
- A: Refactor `BundleConformanceValidator` to use `MarkdownStructure` (add what it lacks, e.g. heading level and
  "line is one link item") and make the rule a strict scan with no exceptions.
- B: Keep the validator's patterns; the guard allows `BundleConformanceValidator` by exact path with a reason (it
  checks bundle file grammar, not wiki page syntax), like `OutboundHttpGuardTest`'s allowlist.
- C: Narrow the rule to page-body code (domain, application, agent) and leave export out.

## Constraints
- T26 Answer: one parser defines heading, section and link; no second regex.
- `BundleConformanceValidator` is the oracle for "every exported bundle passes" (plan 9b checklist); a change to it
  must keep `BundleConformanceValidatorTest` and the export tests green.
- Domain stays JDK-only if `MarkdownStructure` gains methods.
