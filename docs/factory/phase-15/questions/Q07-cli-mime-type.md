---
id: Q07
status: decided
decision: D07
impact: low
blocks: [W03]
---
## Question
How does the pipeline CLI determine the MIME type of `--file <path>`?

## Context
- `UploadSanitizer.admit` refuses any MIME type not in `ParserRegistry.mimeTypes()` (`text/markdown`, `text/plain`,
  `application/pdf`, DOCX); `ParserRegistry.parse` dispatches on it.
- The web path gets the type from the browser; the SPA maps `.md`/`.markdown` → `text/markdown`, `.txt` → `text/plain`
  itself (`frontend/src/app/pages/kb/documents.ts#mimeType`).
- `Files.probeContentType` depends on the OS (often `null` or `text/x-markdown` for `.md`).

## Known options
- A: Extension map in the CLI mirroring the SPA (`.md`, `.markdown`, `.txt`, `.pdf`, `.docx`); else
  `application/octet-stream`, which the sanitizer refuses with its normal message.
- B: `Files.probeContentType` with the extension map as a fallback.
- C: A `--mime` option, defaulted by A.

## Constraints
- Never modify `ParserRegistry` to add a format (CLAUDE.md); the upload allowlist stays the parser registry.
