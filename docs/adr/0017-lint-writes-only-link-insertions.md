# Lint writes only link insertions, and runs inside every ingest before commit

Lint never writes prose. Its model output is a list of `LinkInsertion(pagePath, phrase,
targetPath, fragment)`, and code applies each by wrapping the first eligible occurrence of the
phrase — outside existing links, code spans and headings — only if the target page is live.
Code then asserts that the page text with links stripped is unchanged. "Additive only" is
therefore a type, not a heuristic such as the reference implementation's "reject a write that is
more than 10% shorter". Contradictions and stale claims are reported as findings, never fixed.

Lint runs at three costs:

- **Structural checks** — dangling links, orphans, duplicate titles, dangling supersessions — are
  live SQL, never a run.
- **A link check** runs inside every ingest, on in-memory bodies, **before the commit**.
- **A full review** runs only on request, as its own `LINT` run behind the ingest lease.

The link check sits before the commit because tip-only revert makes any automatic follow-up
write wrong: a separate Lint run seconds after an ingest would put a newer run on the tip of
every page it touched, and each ingest would lose its own revert window to its own Lint.

## Consequences

- Headings are never linked, because supersession anchors to heading slugs.
- The full review's suggestions ("a page mentions X but nothing covers it") are study prompts,
  not generated pages: a page written from the model's own knowledge has no source behind it.
- No scheduled Lint: spending LLM calls on knowledge bases nobody opened is spend without a reader.

Decided in [T10](../wayfinder/tickets/10-lint-operation.md).
