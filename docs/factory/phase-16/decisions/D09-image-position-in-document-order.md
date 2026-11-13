---
id: D09
question: Q09
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q09.r1.tally.md]
adr: 
---
## Decision
Use A: in `PdfParser`, emit each page's IMAGE blocks directly after that page's TEXT block, in content-stream order. In `DocxParser`, emit the IMAGE block(s) directly after the paragraph or table that holds the picture. Assign consecutive positions as blocks are emitted.

## Why
- `PdfParser` emits one TEXT block per page (PdfParser.java:31-37); the page is the finest existing unit, so "after the page text" is document order at that granularity.
- `DocxParser` emits one block per body element (DocxParser.java:42-46); "after the holding element" is exact at that granularity.
- Image and its surrounding text stay adjacent, so `HeadingChunker` and claim block ranges see both together.
- Positions come from `blocks.size()` in the parser, so a later move to B is a parser-only change.
- Plan 16.2 (implementation-plan.md:1489) requires "in document order"; A satisfies it with little code.

## Rejected
- B: exact mid-page order needs a custom `PDFTextStripper`/`PDFStreamEngine` and breaks one-block-per-page; too much code for a low-impact question.
- C: all images at the end contradicts "in document order" and loses locality.

## Dissent
None (single ballot). Risk to watch: PDFs with many images per page whose mid-page position changes chunk or claim attribution; if this shows up, move to B inside `PdfParser` only.

## Implementation notes
- `PdfParser`: per page, add the TEXT block (if non-empty), then that page's IMAGE blocks in content-stream order (e.g. a `PDFStreamEngine` subclass or page resources walk collecting image XObjects in draw order). A page with no text but with images must still emit its IMAGE blocks (the current loop skips empty-text pages; do not skip images).
- `DocxParser`: after the block for a `XWPFParagraph` (from its runs' embedded pictures) or `XWPFTable` (pictures in cell paragraphs), add IMAGE blocks in run order. A paragraph with only a picture and no text: emit the IMAGE block; do not emit an empty TEXT block.
- Positions: always `blocks.size()` at emit time — consecutive, 0-based, no gaps.
- Tests (`PdfParserTest`, `DocxParserTest`): page text then image positions are n, n+1; two images on a page keep stream order; image-only page; DOCX picture inside paragraph lands right after it; picture in a table cell lands right after the table; positions consecutive across the whole document.
