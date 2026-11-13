---
id: D07
question: Q07
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q07.r1.tally.md]
adr: 
---
## Decision
Use A: the pipeline CLI finds the MIME type of `--file <path>` from a fixed, case-insensitive extension map (`.md`, `.markdown` -> `text/markdown`; `.txt` -> `text/plain`; `.pdf` -> `application/pdf`; `.docx` -> the DOCX MIME type). Any other extension gives `application/octet-stream`, which `UploadSanitizer.admit` refuses with its normal message. Do not call `Files.probeContentType` and do not add a `--mime` option.

## Why
- The result is deterministic on all operating systems; `Files.probeContentType` can return `null` or `text/x-markdown` for `.md`, and the sanitizer refuses both (UploadSanitizer.java:38-40).
- The map agrees with the SPA for `.md`/`.markdown`/`.txt` (documents.ts:172-181), so the web path and the CLI path give the same type for the same file.
- The allowlist stays `ParserRegistry.mimeTypes()` (ParserRegistry.java:22-23): the CLI only names a type; the sanitizer decides. `ParserRegistry` is not changed.
- It is the simplest option and it is easy to change later.

## Rejected
- B: OS-dependent result for no gain; the fallback map is still necessary.
- C: speculative flag with no current need; it can be added later on top of A without a break.

## Dissent
None (single ballot). Risk to watch: a new parser format needs one new map entry in the CLI, or the CLI refuses that format although the web path accepts it.

## Implementation notes
- In the CLI command class (package `dev.mindforge.cli`): `private static final Map<String, String> MIME_BY_EXTENSION` and `static String mimeTypeOf(Path file)` — take the text after the last `.` of the file name, lowercase with `Locale.ROOT`; no extension or unknown extension -> `"application/octet-stream"`.
- Use the parser constants for the values: `MarkdownParser.MIME_TYPE`, `PlainTextParser.MIME_TYPE`, `PdfParser.MIME_TYPE`, `DocxParser.MIME_TYPE` (adapters may import all layers).
- Pass the result to the same upload/ingest path as the web controller, so `UploadSanitizer.admit` does the refusal; do not add a second allowlist check in the CLI.
- Unit tests (plain JUnit, AssertJ): `notes.md`, `NOTES.MD`, `a.markdown` -> `text/markdown`; `a.txt` -> `text/plain`; `a.pdf` -> `application/pdf`; `a.docx` -> DOCX type; `a.sh`, `README` (no extension), `archive.tar.gz` -> `application/octet-stream`; and a CLI run with an unsupported file shows the sanitizer's "Unsupported document type" message.
