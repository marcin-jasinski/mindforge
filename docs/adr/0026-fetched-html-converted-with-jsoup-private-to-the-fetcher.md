# Fetched HTML is converted with jsoup, in a converter private to the article fetcher

Status: Accepted (2026-11-14).

Article pages fetched in Phase 17 are converted to content blocks with jsoup, a new runtime dependency. The
converter is infrastructure code in the article fetch adapter and is not a `FormatParser`: `text/html` is not
registered in `ParserRegistry`. The converter removes boilerplate (script, style, noscript, nav, header, footer,
aside), maps `<h1>`–`<h6>` to HEADING blocks with their level, `<pre>` to CODE blocks and other block text to TEXT
blocks, and returns the page `<title>`. It emits plain text only and never passes markup through. `HeadingChunker`
thus sees the same structure that a Markdown upload gives.

## Considered Options

- **A `text/html` `HtmlParser` registered in `ParserRegistry`**: the upload allowlist is built from
  `parserRegistry.mimeTypes()` (`IngestionConfig`), so HTML would become a legal user upload on every channel. The
  plan does not ask for this, and it widens the upload attack surface.
- **JDK only (regex tag stripping)**: no dependency, but it is fragile on malformed real pages, on nested script and
  style, on comments and on entities. Each bug leaks markup or boilerplate into Extract.

## Consequences

- One new runtime dependency (`org.jsoup:jsoup`, MIT), documented in `docs/project/tech-stack.md`.
- HTML remains an illegal upload. To accept HTML uploads later, register a parser that reuses the converter. That
  is a scope change and needs a new decision.
- The converter is the only barrier between fetched markup and Extract. Its tests prove that no tag markup or
  script text survives.

Decided in [D05](../factory/phase-17/decisions/D05-html-to-text-conversion.md) by the dark-factory run `phase-17`.
