---
id: P17-04
title: Convert fetched HTML into a title and plain-text HEADING/TEXT/CODE blocks with a jsoup converter private to the fetcher
status: ready
plan_tasks: [17.2, 17.3]
decisions: [D05]
depends_on: []
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
A package-private `HtmlArticleConverter` in `dev.mindforge.infrastructure.web` turns an HTML string into an
`ArticleText` (title, plain text, ordered `ContentBlock`s). Boilerplate is dropped, headings keep their level, `<pre>`
becomes CODE, and no markup or script text survives. jsoup is a documented runtime dependency. HTML is still not an
upload format.

## Context
- No HTML library is on the classpath; `pom.xml` has commonmark, pdfbox, poi-ooxml.
- `src/main/java/dev/mindforge/domain/model/ContentBlock.java`: factories `heading(...)`, `text(...)`, `code(...)`
  (check their exact signatures; `heading` carries the level in metadata), `image(...)`. `BlockType` has HEADING, TEXT,
  CODE. `HeadingChunker` (`application/ingest/HeadingChunker.java:49`) reads the heading level, so a converted
  article chunks like a Markdown upload.
- `infrastructure/config/IngestionConfig.java:38-41` builds `UploadSanitizer` from `parserRegistry.mimeTypes()`, so
  registering a `text/html` parser would make HTML uploadable. That is forbidden here.
- ADR 0026 (`docs/adr/0026-fetched-html-converted-with-jsoup-private-to-the-fetcher.md`, Accepted) is binding.
- `dev.mindforge.infrastructure.web` does not exist yet; P17-05 adds `HttpArticleSource` to the same package and is the
  converter's only caller. Tests that need package-private access live in
  `src/test/java/dev/mindforge/infrastructure/web/` (runs in surefire).
- The `fast` gate runs Maven offline (`-o`); jsoup is not in `~/.m2` yet, so resolve it once online
  (`mvn -q dependency:resolve` or a normal online build) before the offline gate.
- `docs/project/tech-stack.md` has a "Key Dependencies Summary" table.

## Decisions to apply
**D05 — Decision:** add jsoup as a runtime dependency and convert fetched HTML in an infrastructure class private to
the article fetcher. The converter drops boilerplate (script, style, noscript, nav, header, footer, aside, template,
iframe), maps `<h1>`–`<h6>` to HEADING blocks with their level, `<pre>` to CODE blocks, and paragraphs/list
items/other block text to TEXT blocks, and returns the `<title>`. It emits plain text only, never markup. Do not
register a `text/html` parser in `ParserRegistry`; HTML stays an illegal user upload.

**D05 — Implementation notes:**
- `pom.xml`: add `org.jsoup:jsoup` (current stable, version managed in `<properties>`). Check that its POM has no
  runtime transitive dependencies. Add a row to the dependency table in `docs/project/tech-stack.md` ("HTML → text
  for fetched articles (ADR 0026)").
- Class `HtmlArticleConverter` in `dev.mindforge.infrastructure.web`, package-private. Method
  `ArticleText convert(String html, String baseUrl)`; `record ArticleText(String title, List<ContentBlock> blocks)`
  (package-private, defensive copy of `blocks`).
- Parse with `Jsoup.parse(html, baseUrl)`. Remove the boilerplate elements before you walk the body. Read text with
  `Element.text()`; `wholeText()` for `<pre>` only. Never use `html()`/`outerHtml()` for output. Skip empty blocks and
  assign `position` in document order.
- Do not touch `IngestionConfig.parserRegistry` or `ParserRegistry`.
- The fetcher caps the body size before it calls the converter (P17-05).
- Converter output is the only barrier between page markup and Extract: a test must prove no `<`-tag markup or script
  text survives.

**D07 (title part) — applied here:** the title is `<title>` trimmed; else `<meta property="og:title" content>`; else the
first `<h1>` text; else null. Blank values count as absent. The converter must be deterministic for the same HTML,
because the article's content hash is computed over its text (D07).

**Ticket-writer choice:** `ArticleText` also exposes `String text()` = the blocks' `content` joined with `"\n\n"` in
order (empty string when there are no blocks). P17-05 uses it as `FetchedArticle.text`.

## Acceptance criteria
- [ ] AC1: `pom.xml` declares `org.jsoup:jsoup` with its version in `<properties>` and a comment citing ADR 0026;
  `docs/project/tech-stack.md` has the dependency row.
- [ ] AC2: `<h1>`–`<h3>` become HEADING blocks with levels 1–3, in document order with increasing `position`.
- [ ] AC3: `<pre><code>` becomes one CODE block with whitespace and line breaks kept.
- [ ] AC4: Paragraphs and list items become TEXT blocks; empty or whitespace-only elements produce no block.
- [ ] AC5: Text inside script, style, noscript, nav, header, footer, aside, template and iframe is absent from every
  block.
- [ ] AC6: Entities are decoded (`&amp;` -> `&`, `&nbsp;` -> space-like, `&#x27;` -> `'`); escaped markup in the input
  (`&lt;script&gt;`) appears only as the literal text, and no block contains a real tag from the source
  (`<script`, `<div`, `<a href`).
- [ ] AC7: Malformed HTML (unclosed tags) still yields text blocks.
- [ ] AC8: Title order: `<title>`, then `og:title`, then first `<h1>`, then null; empty input gives null title, no
  blocks and `text() == ""`.
- [ ] AC9: Converting the same HTML twice gives equal `ArticleText` (deterministic).
- [ ] AC10: `ParserRegistry` and `IngestionConfig` are unchanged; the registry has no `text/html` entry.

## Test plan
- Unit (plain JUnit 5 + AssertJ, fixed HTML strings, no Spring):
  `src/test/java/dev/mindforge/infrastructure/web/HtmlArticleConverterTest.java` (new, same package for
  package-private access) for AC2–AC9.
- AC10: a small assertion in an existing unit test of the parser registry if one exists (e.g. under
  `src/test/java/dev/mindforge/unit/infrastructure/parsing/`), else review by diff. AC1 by review.

## Files likely touched
- `pom.xml`
- `docs/project/tech-stack.md`
- `src/main/java/dev/mindforge/infrastructure/web/HtmlArticleConverter.java` (new)
- `src/main/java/dev/mindforge/infrastructure/web/ArticleText.java` (new, or nested in the converter)
- `src/test/java/dev/mindforge/infrastructure/web/HtmlArticleConverterTest.java` (new)

## Out of scope
- Any `text/html` `DocumentParser`, any change to `ParserRegistry`, `IngestionConfig` or `UploadSanitizer`.
- HTTP fetching, body size cap, content-type check (P17-05).
- Images in articles; link extraction from articles (no recursion).

## Notes
