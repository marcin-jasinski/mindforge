---
id: P16-04
title: Emit IMAGE blocks from PdfParser after each page's text, in content-stream order
status: ready
plan_tasks: [16.1, 16.3]
decisions: [D09, D05]
depends_on: [P16-03]
size: M
issue: 14
commit:
fix_rounds: 0
---
## Goal
An uploaded PDF yields, for each page, its TEXT block (if the page has text) followed by one IMAGE block per accepted
image on that page, in the order the page draws them. Positions stay consecutive and 0-based across the document.
A PDF with more than 100 distinct accepted images is rejected at upload.

## Context
- `src/main/java/dev/mindforge/infrastructure/parsing/PdfParser.java`: loads with `Loader.loadPDF`, runs
  `PDFTextStripper` per page, adds `ContentBlock.text(text, blocks.size())` for non-empty pages only; returns
  `ParsedDocument(joinedText, blocks, metadata)` where joined text = block contents joined by `"\n\n"`. An
  `IOException` becomes `UploadRejectedException("The document is not a readable PDF", e)`.
- P16-01: `ContentBlock.image(ImageInput image, int position)` is the only way to build an IMAGE block (content `""`).
- P16-03: `dev.mindforge.infrastructure.parsing.ImageExtractor` — one instance per `parse` call;
  `accept(...)` returns `Optional<ImageInput>` after filters (raster only, >= 64 px, <= 5 MB, SHA-256 dedup);
  `checkCount(int)` throws `UploadRejectedException` above 100. Read its code for the exact overloads.
- Phase 16 plan task 16.1: "Extend `PdfParser` ... to emit `ContentBlock`s with `BlockType.IMAGE` carrying bytes +
  MIME type." 16.3: "`PdfParserTest` image extraction."
- Test fixtures are built in the test with PDFBox (plan 4.6), never stored binaries. Unit tests load no Spring.
- Never modify `ParserRegistry`. No new dependency (PDFBox 3.0.3 present; `javax.imageio` is JDK).

## Decisions to apply
**D09 — Decision:** in `PdfParser`, emit each page's IMAGE blocks directly after that page's TEXT block, in
content-stream order. Assign consecutive positions as blocks are emitted.

**D09 — Implementation notes:**
- Per page, add the TEXT block (if non-empty), then that page's IMAGE blocks in content-stream order (e.g. a
  `PDFStreamEngine` subclass or page resources walk collecting image XObjects in draw order). A page with no text but
  with images must still emit its IMAGE blocks (the current loop skips empty-text pages; do not skip images).
- Positions: always `blocks.size()` at emit time — consecutive, 0-based, no gaps.
- Tests (`PdfParserTest`): page text then image positions are n, n+1; two images on a page keep stream order;
  image-only page; positions consecutive across the whole document.

**D05 — Implementation notes (PDF part):**
- Per page (D09 order), walk image XObjects; DCT (`JPEG`) streams pass their raw bytes as `image/jpeg`; others via
  `PDImageXObject.getImage()` then PNG; a decode `IOException`/runtime failure skips that image, it does not reject
  the upload. Emit with `ContentBlock.image(image, blocks.size())`.
- Same image on three pages is emitted once, at the first position. After the last page, call `checkCount` so more
  than 100 distinct images throws `UploadRejectedException("The document has " + n + " images (limit 100)")`.
- Dimension check may use `PDImageXObject.getWidth()/getHeight()` before decoding.

Additional rule (consequence of P16-01, `content == ""` for IMAGE): the `ParsedDocument` text (stored as the document's
original content) must stay the text of the TEXT blocks only — exclude IMAGE blocks from the join so no empty
separators are added.

## Acceptance criteria
- [ ] AC1: A one-page PDF with text and one 64x64+ image yields TEXT at position 0 and IMAGE at position 1 carrying the
  image's bytes as PNG (`image/png`) or JPEG (`image/jpeg`).
- [ ] AC2: Two different images on one page are emitted in content-stream (draw) order.
- [ ] AC3: A page with an image and no text emits the IMAGE block (no empty TEXT block).
- [ ] AC4: The same image drawn on three pages is emitted once, at its first occurrence.
- [ ] AC5: A JPEG (DCT) image is emitted byte-identical as `image/jpeg`; a lossless image is emitted as decodable PNG.
- [ ] AC6: An image smaller than 64 px is not emitted; an image that fails to decode is skipped and the parse succeeds.
- [ ] AC7: A PDF with 101 distinct accepted images throws `UploadRejectedException`.
- [ ] AC8: Positions are consecutive and 0-based over the whole document; `ParsedDocument.text()` contains only page
  text (unchanged for PDFs without images; existing `PdfParserTest` cases pass).

## Test plan
- Unit (plain JUnit + AssertJ): extend `src/test/java/dev/mindforge/unit/infrastructure/parsing/PdfParserTest.java`.
  Build PDFs in the test with `PDDocument`, `PDPageContentStream`, `LosslessFactory.createFromImage` and
  `JPEGFactory.createFromImage` (images from `BufferedImage`); `make*` static factories (e.g. `makePdfWithImages`).
  For AC7, draw 101 distinct small-but-valid (>= 64 px) images — keep the test fast.

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/parsing/PdfParser.java`
- `src/test/java/dev/mindforge/unit/infrastructure/parsing/PdfParserTest.java`

## Out of scope
- Exact mid-page position of images between lines of text (D09 rejected option B).
- DOCX (P16-05), Markdown or plain-text images.
- Changing `ParserRegistry`, adding settings or dependencies, changing `ImageExtractor` limits.

## Notes
