---
id: Q10
status: answered
impact: low
blocks: []
answered_by: docs/project/implementation-plan.md#Phase 16 — Image Analysis (Goal, 16.1)
---
## Question
Do Markdown and plain-text uploads (e.g. `![alt](url)`) also produce IMAGE blocks in this phase?

## Context
`MarkdownParser` and `PlainTextParser` emit no IMAGE blocks; a Markdown image is a URL, not embedded bytes, and
fetching it would be outbound HTTP (Phase 17's `EgressPolicy`).

## Known options
- Settled: no. The goal is "images extracted from uploaded PDFs and DOCX files"; 16.1 extends `PdfParser` and
  `DocxParser` only.

## Constraints
All outbound HTTP goes through `EgressPolicy` (Phase 17 checklist) — another reason not to fetch Markdown image URLs.
