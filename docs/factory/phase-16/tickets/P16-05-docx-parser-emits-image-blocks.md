---
id: P16-05
title: Emit IMAGE blocks from DocxParser right after the paragraph or table that holds the picture
status: ready
plan_tasks: [16.1, 16.3]
decisions: [D09, D05]
depends_on: [P16-03]
size: M
issue: 15
commit:
fix_rounds: 0
---
## Goal
An uploaded DOCX yields IMAGE blocks for the pictures embedded in its paragraphs and table cells, each placed
directly after the block of the paragraph or table that holds it, in run order. Positions stay consecutive and
0-based. A DOCX with more than 100 distinct accepted images is rejected at upload.

## Context
- `src/main/java/dev/mindforge/infrastructure/parsing/DocxParser.java`: iterates `docx.getBodyElements()`; a
  `XWPFParagraph` becomes a HEADING (style name `heading N`) or TEXT block, a `XWPFTable` becomes one TEXT block
  (`tableText`); blank blocks are not added; position = `blocks.size()`. Pictures inside runs are ignored today.
  `ParsedDocument` text = block contents joined by `"\n\n"`. IO/POI errors become
  `UploadRejectedException("The document is not a readable DOCX file", e)`.
- P16-01: `ContentBlock.image(ImageInput image, int position)` is the only way to build an IMAGE block (content `""`).
- P16-03: `dev.mindforge.infrastructure.parsing.ImageExtractor` — one instance per `parse` call;
  `accept(...)` returns `Optional<ImageInput>` after filters (raster only, >= 64 px, <= 5 MB, SHA-256 dedup; EMF, WMF,
  SVG skipped; GIF/BMP/TIFF re-encoded to PNG); `checkCount(int)` throws `UploadRejectedException` above 100.
- Test fixtures are built in the test with POI (plan 4.6), never stored binaries. Unit tests load no Spring.
- Never modify `ParserRegistry`. No new dependency (POI 5.4.0 present).

## Decisions to apply
**D09 — Decision:** in `DocxParser`, emit the IMAGE block(s) directly after the paragraph or table that holds the
picture. Assign consecutive positions as blocks are emitted.

**D09 — Implementation notes:**
- After the block for a `XWPFParagraph` (from its runs' embedded pictures) or `XWPFTable` (pictures in cell
  paragraphs), add IMAGE blocks in run order. A paragraph with only a picture and no text: emit the IMAGE block; do not
  emit an empty TEXT block.
- Positions: always `blocks.size()` at emit time — consecutive, 0-based, no gaps. (Today `position` is computed before
  the blank check; make sure an image-only paragraph does not leave a gap.)
- Tests (`DocxParserTest`): DOCX picture inside paragraph lands right after it; picture in a table cell lands right
  after the table; positions consecutive across the whole document.

**D05 — Implementation notes (DOCX part):**
- `XWPFPictureData.getData()` with its picture type mapped to MIME (PNG -> `image/png`, JPEG -> `image/jpeg`, GIF,
  BMP, TIFF -> their `image/*` type for re-encoding; EMF -> `image/x-emf`, WMF -> `image/x-wmf`, SVG ->
  `image/svg+xml`, which the extractor skips); pass through the same `ImageExtractor`.
- Same picture used twice is emitted once, at the first position. After the body, call `checkCount`.

Additional rule (consequence of P16-01, `content == ""` for IMAGE): the `ParsedDocument` text must stay the text of
the non-IMAGE blocks only — exclude IMAGE blocks from the join.

## Acceptance criteria
- [ ] AC1: A paragraph with text and a picture yields the paragraph's TEXT block at n and the IMAGE block at n+1.
- [ ] AC2: A paragraph holding only a picture yields only an IMAGE block (no blank TEXT block, no position gap).
- [ ] AC3: A picture inside a table cell yields an IMAGE block directly after the table's TEXT block.
- [ ] AC4: Two pictures in one paragraph keep run order.
- [ ] AC5: A PNG picture is emitted byte-identical as `image/png`; an EMF picture is not emitted and the parse succeeds.
- [ ] AC6: The same picture inserted twice is emitted once; a picture smaller than 64 px is not emitted.
- [ ] AC7: A DOCX with 101 distinct accepted pictures throws `UploadRejectedException`.
- [ ] AC8: Positions are consecutive and 0-based; `ParsedDocument.text()` has no extra separators from IMAGE blocks;
  existing `DocxParserTest` cases pass.

## Test plan
- Unit (plain JUnit + AssertJ): extend `src/test/java/dev/mindforge/unit/infrastructure/parsing/DocxParserTest.java`.
  Build DOCX in the test with `XWPFDocument`, `XWPFRun.addPicture(InputStream, Document.PICTURE_TYPE_PNG, ...)`, tables
  via `createTable()`; images from `BufferedImage` + `ImageIO`; for EMF, add a picture with
  `Document.PICTURE_TYPE_EMF` and arbitrary bytes. `make*` static factories.

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/parsing/DocxParser.java`
- `src/test/java/dev/mindforge/unit/infrastructure/parsing/DocxParserTest.java`

## Out of scope
- Floating/anchored shapes outside runs, headers/footers, charts (only `XWPFRun.getEmbeddedPictures()` pictures in body
  paragraphs and table-cell paragraphs).
- PDF (P16-04), Markdown or plain-text images; `ParserRegistry`; settings; new dependencies.

## Notes
