---
id: D07
question: Q07
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q07.r1.tally.md]
adr:
---
## Decision
Use A. Take the lesson title from the converter's `<title>` (else `og:title`, else the first `<h1>`) and pass it to
`LessonIdentity.resolve` under the frontmatter `title` key; the filename fallback is the last non-empty URL path
segment (else the host). Store the final URL (after redirects), cut to 1000 chars, in `sourceFilename`; `mimeType` is
`text/html`; `uploadedBy` is the parent document's uploader; `source` is `UploadSource.ARTICLE`; the content hash is
`ContentHash.compute` over the extracted text in UTF-8. Insert through a new `IngestionService` entry method that
shares the existing transaction body (lock, dedup, lesson rule, `distinctTitle`, enqueue). No migration.

## Why
- `source_filename` is `VARCHAR(1000) NOT NULL` (V1__baseline.sql:33) and `PageRenderer.documentLines` already shows
  it in citations, so the URL gives provenance with no migration or DTO change.
- Dedup is a unique index on (kb, content_hash) that covers ARTICLE rows (V1__baseline.sql:45-46); a raw-HTML hash
  changes per fetch of a dynamic page, so only a hash over extracted text keeps dedup working (C out).
- `LessonIdentity.resolve` (LessonIdentity.java:45-62) already cleans (`singleLine`) and caps (`cut`, 200) the title
  and throws instead of a placeholder, so it is reused unchanged.
- `ingest()` runs `uploadPolicy.admit` and `parser.parse` (IngestionService.java:82-83); D05 forbids a `text/html`
  parser, so articles need their own entry method around the same transaction body.
- `uploaded_by` is a NOT NULL FK; the parent's uploader is the only user in context (D01's event carries it).

## Rejected
- B: a `source_url` column, migration and DTO work for no stated requirement (minimal-implementation).
- C: raw-bytes hash defeats dedup for dynamic pages.

## Dissent
None. Risk to watch: a URL longer than 1000 chars is cut, so its citation is a truncated URL (display only; dedup
uses the hash). If a later need is to query or export by URL, revisit B.

## Implementation notes
- `IngestionService`: add `UUID ingestArticle(UUID kbId, UUID userId, URI finalUrl, String title, String text,
  List<ContentBlock> blocks)` (name may vary; `kbId` first). Extract the transaction body of `ingest()` (lines 86-115)
  into a private method that takes the resolved `LessonIdentity`, hash, filename, mimeType, text, blocks, source,
  uploader and `newVersion`; `ingest()` and `ingestArticle()` both call it. Do not call `uploadPolicy.admit` or
  `parser.parse` for articles. `newVersion` is always false for articles.
- Lesson identity: `LessonIdentity.resolve(title == null ? Map.of() : Map.of("title", title), lastPathSegment(url))`.
  `KEY_FRONTMATTER_TITLE` is private (LessonIdentity.java:38): make it public with a Javadoc line (like
  `KEY_PDF_TITLE`) and use it, do not repeat the literal. Never pass a `lesson_id` key from HTML.
- `sourceFilename` = `finalUrl.toString()`, cut to 1000 chars (code points safe). The stored filename and the
  fallback filename are different values: do not pass the full URL to `resolve`.
- Hash: `ContentHash.compute(text.getBytes(StandardCharsets.UTF_8))`, where `text` is the converter's output (D05);
  the converter must be deterministic for the same HTML.
- `uploadedBy` = the parent's uploader from the `DocumentAccepted` event (D01). Do not publish `DocumentAccepted`
  for ARTICLE inserts (no recursive fetch; D01).
- The fetcher service catches `LessonAlreadyExistsException` and `LessonIdentityException` per article, logs at INFO
  with kbId and URL, and goes on to the next article (skip, no new version — T18 § 1 / Q14).
- Tests (unit, Mockito, `make*` factories): title from `<title>`; fallback to `og:title`, then `<h1>`, then URL path
  segment, then host; no title and no segment resolves nothing -> skipped and logged; same text fetched twice ->
  second call returns the existing document id and queues no run; lesson already exists -> skipped, no insert; taken
  title in another lesson -> `distinctTitle` applied; URL over 1000 chars is cut; row has `ARTICLE`, `text/html`,
  parent's uploader, and the article's kbId.
