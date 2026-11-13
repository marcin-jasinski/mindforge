---
id: P16-03
title: Add the ImageExtractor that filters, re-encodes, deduplicates and caps a document's images
status: done
plan_tasks: [16.1]
decisions: [D05]
depends_on: [P16-01]
size: M
issue: 13
commit:
fix_rounds: 1
---
## Goal
A parser-side helper `dev.mindforge.infrastructure.parsing.ImageExtractor` decides, per extracted image, whether it
becomes an `ImageInput`: decodable raster only, PNG/JPEG kept as is, other rasters re-encoded to PNG, small,
oversized and duplicate images skipped, and more than 100 distinct images rejects the upload. `PdfParser` and
`DocxParser` use it in P16-04 and P16-05.

## Context
- P16-01 added `dev.mindforge.domain.model.ImageInput(byte[] data, String mimeType)` (validates non-empty bytes and an
  `image/*` MIME type).
- Parsers live in `src/main/java/dev/mindforge/infrastructure/parsing/` (`PdfParser`, `DocxParser`, ...). They take
  no configuration today (`IngestionConfig.java:32-33`) and already reject bad input with
  `dev.mindforge.domain.model.UploadRejectedException` (`PdfParser.java:44-45`), mapped to 4xx in
  `GlobalExceptionHandler.java:121-122`.
- Upload rejection happens in `IngestionService.ingest` before any row is stored (the parse runs before the
  transaction), so a rejected document is never stored.
- No new dependency: `javax.imageio` is JDK; PDFBox 3.0.3 and POI 5.4.0 are present.
- Logger named `log` (`private static final Logger log = LoggerFactory.getLogger(ImageExtractor.class)`).
- Never modify `ParserRegistry`.

## Decisions to apply
**D05 — Decision:** parsers emit only decodable raster images; keep PNG and JPEG bytes as they are, re-encode every
other decodable raster to PNG with `javax.imageio`, and skip EMF, WMF, SVG and anything that does not decode. Skip
images narrower or shorter than 64 px. Emit each distinct image (SHA-256 of the emitted bytes) once per document, at
its first occurrence. Skip one image whose emitted bytes exceed 5 MB. After filter and dedup, if a document has more
than 100 IMAGE blocks, reject the upload with `UploadRejectedException` (fail loudly, before any row is stored). No
opt-out switch in Phase 16.

**D05 — Implementation notes:**
- New package-private-or-public helper `dev.mindforge.infrastructure.parsing.ImageExtractor` (one instance per `parse`
  call, not a bean): constants `MIN_DIMENSION = 64`, `MAX_IMAGE_BYTES = 5 * 1024 * 1024`,
  `MAX_IMAGES_PER_DOCUMENT = 100`; holds a `Set<String>` of SHA-256 hex digests. Method
  `Optional<ImageInput> accept(byte[] bytes, String mimeType, int width, int height)` (or a `BufferedImage` overload
  for decoded PDF images): returns empty for small, undecodable, non-raster, over-byte-cap or duplicate images; logs
  each skip with `log` (INFO for small/duplicate/non-raster counts, WARN for decode failure and over-byte-cap) naming
  the reason, never the bytes. After the last block, `checkCount(int images)` throws
  `UploadRejectedException("The document has " + n + " images (limit 100)")`.
- Format rules: `image/png` and `image/jpeg` pass through unchanged; GIF, BMP, TIFF (and any other
  `ImageIO.read`-decodable raster) re-encode to PNG; `image/x-emf`, `image/x-wmf`, `image/svg+xml` skip. Dimension
  check uses the decoded image (or PDFBox `PDImageXObject.getWidth()/getHeight()` before decoding).
- `ParserRegistry` unchanged; no new dependency; no `ProcessingSettings` field; no switch.
- Tests (`ImageExtractorTest`, plain JUnit + AssertJ): 32x32 image skipped; 64x64 kept; same image three times
  accepted once; BMP/GIF re-encoded with MIME `image/png` and decodable by ImageIO; JPEG kept as `image/jpeg`
  byte-identical; EMF/WMF/SVG MIME skipped; undecodable bytes skipped without throwing; image over 5 MB skipped;
  101 distinct images -> `checkCount` throws `UploadRejectedException`, 100 do not; duplicates do not count toward the
  cap.
- Dissent risk to watch: silent loss from the 5 MB skip and from images that cannot be decoded — a WARN log line each.

## Acceptance criteria
- [x] AC1: `ImageExtractor` exists with the three constants above (64, 5 MB, 100) and is not a Spring bean.
- [x] AC2: An image narrower or shorter than 64 px is skipped; a 64x64 image is accepted.
- [x] AC3: PNG and JPEG input is returned byte-identical with MIME `image/png` / `image/jpeg`.
- [x] AC4: GIF and BMP input is returned as PNG (`image/png`) that `ImageIO.read` decodes.
- [x] AC5: EMF, WMF and SVG MIME types and undecodable bytes are skipped without an exception.
- [x] AC6: An image whose emitted bytes exceed 5 MB is skipped with a WARN log.
- [x] AC7: The same emitted bytes accepted a second time return empty (SHA-256 dedup); duplicates do not count.
- [x] AC8: `checkCount(101)` throws `UploadRejectedException` with message "The document has 101 images (limit 100)";
  `checkCount(100)` does not.
- [x] AC9: No log line contains image bytes or base64.

## Test plan
- Unit (plain JUnit + AssertJ, no Spring): `src/test/java/dev/mindforge/unit/infrastructure/parsing/ImageExtractorTest.java`
  (new). Build images in the test with `BufferedImage` + `ImageIO.write` (PNG/JPEG/GIF/BMP); a large-noise image or
  an accepted-bytes path for the 5 MB case; `make*` static factories for images. No stored binary fixtures.

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/parsing/ImageExtractor.java` (new)
- `src/test/java/dev/mindforge/unit/infrastructure/parsing/ImageExtractorTest.java` (new)

## Out of scope
- Calling the helper from `PdfParser`/`DocxParser` (P16-04, P16-05).
- A configuration property, opt-out switch, or `ProcessingSettings` field for the limits.
- Any new library (no TwelveMonkeys, no JBIG2/JPX plugins).
- Documentation of the limits (P16-10).

## Notes
- Decided: `accept(byte[] bytes, String mimeType)` decodes with `ImageIO.read` and takes the size from the decoded image (no width/height parameters), plus `accept(BufferedImage)` that emits PNG for decoded PDF images, because DOCX pictures have no dimensions before decode and "decodable raster only" needs a decode for PNG/JPEG pass-through too. A JPEG that ImageIO cannot decode (for example CMYK) is skipped with a WARN.
- Tester r1 AC map (ImageExtractorTest): AC1 shouldHaveTheLimitsAndNoAnnotation; AC2 shouldSkipAnImageNarrowerOrShorterThan64Px + shouldAcceptA64By64Image; AC3 shouldKeepPngAndJpegBytesAsTheyAre; AC4 shouldReEncodeGifAndBmpToPng; AC5 shouldSkipVectorFormatsAndUndecodableBytesWithoutAnException; AC6 shouldSkipAnImageOver5MbWithAWarning; AC7 shouldAcceptTheSameImageOnceAndNotCountDuplicates; AC8 shouldRejectMoreThan100Images; AC9 shouldNeverLogImageBytes.

### Deferred findings
- correctness/C3 minor 0.6 src/main/java/dev/mindforge/infrastructure/parsing/ImageExtractor.java:43: A null `mimeType` throws `NullPointerException` instead of a skip, because `Set.of(...).contains(null)` throws.
- spec-security/S2 minor 0.5 src/main/java/dev/mindforge/infrastructure/parsing/ImageExtractor.java:60: PNG/JPEG pass-through trusts the declared MIME type, not the decoded format, so bytes of another format declared as image/png or image/jpeg are emitted unchanged with a wrong MIME type, against D05 "re-encode every other decodable raster to PNG".
