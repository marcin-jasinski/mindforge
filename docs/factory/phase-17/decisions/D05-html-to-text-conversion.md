---
id: D05
question: Q05
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q05.r1.tally.md]
adr: docs/adr/0026-fetched-html-converted-with-jsoup-private-to-the-fetcher.md
---
## Decision
Use A: add jsoup as a runtime dependency and convert fetched HTML in an infrastructure class private to the article
fetcher. The converter drops boilerplate (script, style, noscript, nav, header, footer, aside, template, iframe), maps
`<h1>`–`<h6>` to HEADING blocks with their level, `<pre>` to CODE blocks, and paragraphs/list items/other block text to
TEXT blocks, and returns the `<title>`. It emits plain text only, never markup. Do not register a `text/html` parser in
`ParserRegistry`; HTML stays an illegal user upload.

## Why
- Registering `text/html` widens the upload allowlist, because `UploadSanitizer` is built from
  `parserRegistry.mimeTypes()` (`IngestionConfig.java:38-41`). Plan 17.2 (`implementation-plan.md:1526-1527`) asks
  only for "HTML → text" in the fetcher, so B is an unrequested change to the upload attack surface.
- No HTML library is on the classpath (`pom.xml:188-205`). A JDK regex stripper (C) cannot handle malformed markup,
  nested script/style, comments and the full entity table, and each bug leaks markup or boilerplate into Extract.
- `BlockType` already has HEADING/TEXT/CODE and `ContentBlock.heading` carries the level, so `HeadingChunker`
  (`HeadingChunker.java:49`) gets the same structure as a Markdown upload.
- One documented library meets "minimal dependencies, documented" (`conventions.md:15-16`). The decision adds a
  runtime dependency, so it is class `adr`.

## Rejected
- B: it makes HTML a legal upload on every channel through the registry-derived allowlist. The plan does not ask for this.
- C: hand-rolled HTML parsing of untrusted, malformed pages is incorrect by design.

## Dissent
None. Risk to watch: the converter output is the only barrier between page markup and Extract. A test must prove
that no `<`-tag markup or script text survives.

## Implementation notes
- `pom.xml`: add `org.jsoup:jsoup` (current stable, version managed in `<properties>`). Check that its POM has no
  runtime transitive dependencies. Add a row to the dependency table in `docs/project/tech-stack.md` ("HTML → text
  for fetched articles (ADR 0026)"). Note: the `fast` gate runs Maven offline (`-o`), so resolve the artifact once online.
- Class `HtmlArticleConverter` in the article fetch adapter package under `dev.mindforge.infrastructure` (the same
  package as the fetcher, package-private if the fetcher is its only caller). Method
  `ArticleText convert(String html, String baseUrl)`; `record ArticleText(String title, List<ContentBlock> blocks)`.
  Title: `<title>` trimmed, else the first `<h1>`, else null (Q07 decides the fallback).
- Parse with `Jsoup.parse(html, baseUrl)`. Remove the boilerplate elements before you walk the body. Read text with
  `Element.text()`/`wholeText()` for `<pre>` only. Never use `html()`/`outerHtml()` for output. Skip empty blocks and
  assign `position` in document order.
- Do not touch `IngestionConfig.parserRegistry` or `ParserRegistry`.
- The fetcher caps the body size before it calls the converter (the HTTP response limit belongs to the fetcher).
- Unit tests (plain JUnit, no Spring): headings h1–h3 become HEADING with levels 1–3; `<pre><code>` becomes CODE with
  whitespace kept; script/style/nav/footer/aside text is absent; malformed HTML (unclosed tags) still gives text;
  entities (`&amp;`, `&nbsp;`, `&#x27;`) are decoded; the title is returned; no output block text contains `<script`
  or other tag markup from an escaped-input fixture; empty input gives no blocks and a null title.
- Optional guard test: `ParserRegistry` bean has no `text/html` key.
