---
id: Q07
status: decided
decision: D07
impact: medium
blocks: [W03, W04]
---
## Question
How is an article `Document` filled in: where its lesson title comes from, what `sourceFilename` and `mimeType` hold
(where the URL is recorded), who `uploadedBy` is, and what the content hash covers?

## Context
- Plan 17.2: "`UploadSource.ARTICLE`, lesson identity from the article title".
- `LessonIdentity.resolve(metadata, filename)` takes frontmatter `title`, PDF `Title`, else the filename stem; it throws
  `LessonIdentityException` when nothing resolves. An article title is not under any of these keys today.
- `documents.source_filename` is `VARCHAR(1000) NOT NULL`; `PageRenderer.documentLines` renders it in citations as
  "<filename>, uploaded <date>". Storing the URL there shows provenance with no migration; a new `source_url` column
  needs a Flyway migration and DTO changes.
- `uploaded_by` is `NOT NULL` with an FK to `users`. The parent document's uploader is the only user in context.
- Dedup (`uq_documents_kb_content_hash`) applies to ARTICLE rows; `ContentHash.compute(bytes)` over raw HTML differs on
  every fetch of a dynamic page; over extracted text it is stable.
- `IngestionService` gives a distinct title when a new lesson's title is already taken (`distinctTitle`).

## Known options
- A: Title from `<title>` (else `og:title`, else first `<h1>`), passed as the frontmatter-title metadata key; filename
  fallback = last URL path segment. `sourceFilename` = the final URL (after redirects), cut to 1000 chars;
  `mimeType` = `text/html`; `uploadedBy` = parent's uploader; hash over the extracted text. Reuse the
  `IngestionService` transaction (lock, dedup, lesson rule, distinct title) through a new entry method.
- B: As A, but a new `documents.source_url` column (migration) and `sourceFilename` = a slug of the title.
- C: As A, but hash over raw response bytes.

## Constraints
- Lesson identity is deterministic and never a placeholder (`ai_agents.md` § "Lesson Identity").
- Existing lesson id → skip and log, no new version (T18 § 1; answered in Q14).
- Migrations reversible and small if B (`docs/standards/backend/migrations.md`).
- Tenancy: the article goes into the parent's knowledge base; `kbId` first on every port call.
