---
id: D05
question: Q05
status: decided
method: majority
class: local
votes: { pragmatist: A, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q05.r1.tally.md]
adr: 
---
## Decision
Use B: parsers emit only decodable raster images; keep PNG and JPEG bytes as they are, re-encode every other decodable raster to PNG with `javax.imageio`, and skip EMF, WMF, SVG and anything that does not decode. Skip images narrower or shorter than 64 px. Emit each distinct image (SHA-256 of the emitted bytes) once per document, at its first occurrence. Skip one image whose emitted bytes exceed 5 MB. After filter and dedup, if a document has more than 100 IMAGE blocks, reject the upload with `UploadRejectedException` (fail loudly, before any row is stored). No opt-out switch in Phase 16.

## Why
- ai_agents.md:91 "Fail loudly, never truncate": a bound that fails, like the claim cap (IngestPipeline.java:247-251), is the existing pattern; two ballots ask for it.
- D01: every run attempt re-describes every image under the shared permit pool, so the image count is a direct per-attempt cost; a cap bounds it.
- The count is deterministic per document, so the check belongs at parse time: parsers already reject with `UploadRejectedException` (PdfParser.java:44-45, mapped to 4xx in GlobalExceptionHandler.java:121-122). The bytes are never stored in `content_blocks`, and a run cannot fail the same way on every retry.
- Filter and dedup drop decoration (logos, rules, repeated headers), not content; they are documented filters, not a truncation. Unsupported formats would fail inside the provider retry/breaker wrap (D03).
- ImageIO is JDK: no new dependency. A constant, not a setting: parsers take no configuration today (IngestionConfig.java:32-33), and no measured need exists (minimal-implementation).
- 100, not 50: slide-deck PDFs carry one diagram per slide; 100 matches `maxPageTasksPerRun` scale and is still a bounded cost.
- No switch: 2 of 3 ballots; descriptions in pages are the Phase 16 completion goal. Easy to add later.

## Rejected
- A: no bound on VISION calls per document; each retry re-pays all of them.
- C: sends formats the vision model rejects and describes every logo and repeated header.
- B2 (counted omission over the cap): needs a run counter and describes an arbitrary first N; no ballot chose it.

## Dissent
Pragmatist (A): a cap adds a failure mode with no measured need. Risk to watch: real uploads rejected for more than 100 distinct images; if so, raise the constant or move it to a `mindforge.*` property. Also watch silent loss from the 5 MB skip and from JPX/JBIG2 images PDFBox cannot decode without plugins (WARN log only).

## Implementation notes
- New package-private-or-public helper `dev.mindforge.infrastructure.parsing.ImageExtractor` (one instance per `parse` call, not a bean): constants `MIN_DIMENSION = 64`, `MAX_IMAGE_BYTES = 5 * 1024 * 1024`, `MAX_IMAGES_PER_DOCUMENT = 100`; holds a `Set<String>` of SHA-256 hex digests. Method `Optional<ImageInput> accept(byte[] bytes, String mimeType, int width, int height)` (or a `BufferedImage` overload for decoded PDF images): returns empty for small, undecodable, non-raster, over-byte-cap or duplicate images; logs each skip with `log` (INFO for small/duplicate/non-raster counts, WARN for decode failure and over-byte-cap) naming the reason, never the bytes. After the last block, `checkCount(int images)` throws `UploadRejectedException("The document has " + n + " images (limit 100)")`.
- Format rules: `image/png` and `image/jpeg` pass through unchanged; GIF, BMP, TIFF (and any other `ImageIO.read`-decodable raster) re-encode to PNG; `image/x-emf`, `image/x-wmf`, `image/svg+xml` skip. Dimension check uses the decoded image (or PDFBox `PDImageXObject.getWidth()/getHeight()` before decoding).
- `PdfParser`: per page (D09 order), walk image XObjects; DCT (`JPEG`) streams pass their raw bytes as `image/jpeg`; others via `PDImageXObject.getImage()` then PNG; a decode `IOException`/runtime failure skips that image, it does not reject the upload. Emit with `ContentBlock.image(image, blocks.size())` (D02).
- `DocxParser`: `XWPFPictureData.getData()` with its picture type mapped to MIME; same helper.
- `ParserRegistry` unchanged; no new dependency; no `ProcessingSettings` field; no switch.
- Docs: add an "Images" paragraph to `docs/standards/backend/ai_agents.md` (or the ingest section of architecture docs) and to `docs/project/deployment.md` stating the filters, the 5 MB skip and the 100-image rejection.
- Tests (`ImageExtractorTest`, `PdfParserTest`, `DocxParserTest`, plain JUnit + AssertJ): 32x32 image skipped; 64x64 kept; same image on three pages emitted once at the first position; BMP/GIF re-encoded with MIME `image/png` and decodable by ImageIO; JPEG kept as `image/jpeg` byte-identical; EMF in DOCX skipped; undecodable bytes skipped without failing the parse; image over 5 MB skipped; 101 distinct images throw `UploadRejectedException`, 100 do not; duplicates do not count toward the cap.
