---
id: Q05
status: decided
decision: D05
impact: medium
blocks: [W03, W07]
---
## Question
How is fetched HTML turned into the article's text and content blocks: with a new dependency (jsoup) or JDK code, and
through a `text/html` parser registered in `ParserRegistry` or a converter private to the fetcher?

## Context
- Plan 17.2: "HTML → text". No HTML library on the classpath (`pom.xml`: commonmark, pdfbox, poi-ooxml).
- `HeadingChunker` splits on HEADING blocks; keeping `<h1>`–`<h6>` as HEADING blocks gives Extract the same structure
  a Markdown upload has. Boilerplate (nav, footer, script, style) should not reach Extract.
- `IngestionConfig.parserRegistry` + `UploadSanitizer(…, parserRegistry.mimeTypes())`: registering `text/html` as a
  `FormatParser` also makes HTML a legal user upload on every channel — a scope change the plan does not ask for.
- The lesson title comes from the article (Q07), so the converter must also return the `<title>`.
- Raw HTML from a page must never reach a page body as markup (bodies render with raw HTML escaped; the writer drafts
  are validated).

## Known options
- A: Add jsoup (MIT, no transitive deps): parse, drop script/style/nav/header/footer/aside, emit HEADING/TEXT/CODE
  blocks and the title; private to the fetcher (not registered). Robust against broken HTML; one new dependency,
  documented in `docs/project/tech-stack.md`.
- B: A, but as an `HtmlParser` registered in `ParserRegistry` for `text/html`; HTML uploads become possible too.
- C: JDK only (regex/tag stripping). No dependency; fragile on real pages, entity decoding by hand.

## Constraints
- Never modify `ParserRegistry` to add a format — register a parser (CLAUDE.md), if B.
- Minimal dependencies, documented (`docs/standards/global/conventions.md`; `docs/project/tech-stack.md`).
- HTML parsing is I/O-adjacent infrastructure code (`dev.mindforge.infrastructure`), not domain.
