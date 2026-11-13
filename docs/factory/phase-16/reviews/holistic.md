---
lens: holistic
ticket: phase-16 (2b45984..HEAD)
verdict: approve
---
- id: H1
  severity: minor
  confidence: 0.7
  location: src/main/java/dev/mindforge/application/service/IngestionService.java:162
  claim: The single-document GET (`IngestionService.document`) and `remove` (line 177) still read through `DocumentRepository.findById`, which loads `content_blocks` with all image bytes, but neither needs the blocks. This goes against the read-path rule of ADR 0020 that P16-02 applied only to the list and the dedup lookup.
  evidence: ADR 0020 Consequences says "Read paths that do not need blocks (the document list, the dedup lookup) must not load them." `DocumentController.java:76` maps `ingestion.document(...)` to `DocumentResponse`, which excludes `contentBlocks` (DocumentResponse.java:7). `remove()` calls `findById(...).isEmpty()` only as an existence check, inside the knowledge-base row-lock transaction. For a PDF with 100 images of up to 5 MB each, every GET /documents/{id} and every remove loads and Jackson-decodes about 500 MB of base64 JSONB that nobody uses. The P16-02 javadoc on `findById` says "The whole document, its content blocks included". The new projection (`DocumentWithoutBlocks`) already exists and fits both callers.
  fix: Add `findWithoutBlocksById` to `DocumentJpaRepository` (the `WITHOUT_BLOCKS` query plus `AND d.documentId = :documentId`). Then use it from `IngestionService.document`, or add a port method such as `findSummaryById`. For `remove`, use an exists query. Keep `findById` for the run.

- id: H2
  severity: minor
  confidence: 0.6
  location: src/main/java/dev/mindforge/infrastructure/parsing/PdfParser.java:101
  claim: `PdfParser.extract` copies the minimum-dimension and pixel-cap checks of `ImageExtractor` (P16-03), including the log text. The image filter rules now live in two classes and can drift apart.
  evidence: PdfParser.java:101-108 re-implements `ImageExtractor.isTooSmall` (ImageExtractor.java:139-141, same log message "Image skipped: {}x{} is smaller than {} px") and the `MAX_PIXELS` guard (ImageExtractor.java:69-71). D05 makes `ImageExtractor` the one helper that "returns empty for small ... images". ai_agents.md "Images" describes one set of filters, but a change to the rule in `ImageExtractor` would not reach PDF images that are checked before the decode.
  fix: Add a package-private `ImageExtractor.acceptsDimensions(int width, int height)` that does both checks and logs once. Call it from `PdfParser.extract` and from `ImageExtractor`'s own paths.
