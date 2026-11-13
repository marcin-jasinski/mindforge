# Phase 16 — Image Analysis: tickets

Execution order. P16-03 and P16-06 can run in parallel after P16-01; P16-04 and P16-05 in parallel after P16-03.

| Id | Title | Depends on | Size | Status |
|---|---|---|---|---|
| [P16-01](P16-01-content-block-carries-image-input.md) | Add the ImageInput value and let an IMAGE content block carry it | — | M | ready |
| [P16-02](P16-02-keep-image-bytes-off-document-list-and-dedup.md) | Keep content blocks off the document list and the dedup lookup, and out of every document response | P16-01 | M | ready |
| [P16-03](P16-03-image-extractor-filters-and-limits.md) | Add the ImageExtractor that filters, re-encodes, deduplicates and caps a document's images | P16-01 | M | ready |
| [P16-04](P16-04-pdf-parser-emits-image-blocks.md) | Emit IMAGE blocks from PdfParser after each page's text, in content-stream order | P16-03 | M | ready |
| [P16-05](P16-05-docx-parser-emits-image-blocks.md) | Emit IMAGE blocks from DocxParser right after the paragraph or table that holds the picture | P16-03 | M | ready |
| [P16-06](P16-06-aigateway-image-overload.md) | Add the AIGateway image overload, routed to the VISION model, through the adapter, decorators and stub | P16-01 | M | ready |
| [P16-07](P16-07-image-describer-model-service.md) | Add the ImageDescriber model service on the VISION tier with its prompt and code-enforced output rules | P16-06 | M | ready |
| [P16-08](P16-08-ingest-run-describes-images-first.md) | Describe a document's images as the ingest run's first step and replace each IMAGE block in place | P16-04, P16-07 | L | ready |
| [P16-09](P16-09-run-report-labels-for-image-description.md) | Label the image description step, its skipped images and its run failure in the run report | P16-08 | S | ready |
| [P16-10](P16-10-close-phase-16-docs.md) | Record the Describe step in ADR 0013 and the architecture, document the image limits, and close Phase 16 | P16-01..P16-09 | S | ready |

## Coverage

| Plan task / item | Tickets |
|---|---|
| 16.1 PDF/DOCX image extraction | P16-01, P16-02, P16-03, P16-04, P16-05 |
| 16.2 `ImageDescriber` | P16-06, P16-07, P16-08, P16-09 |
| 16.3 Unit tests | P16-04, P16-05, P16-07 (plus tests in every ticket) |
| Checklist: VISION routes to the model string | P16-06 |
| Checklist: description appears in pages | P16-08 |
| Close-out docs | P16-10 |

| Decision | Applied by |
|---|---|
| D01 | P16-02, P16-10 (context in P16-08) |
| D02 | P16-01 |
| D03 | P16-01 (`ImageInput`), P16-06 |
| D04 | P16-07, P16-08, P16-09 |
| D05 | P16-03, P16-04, P16-05, P16-10 |
| D06 | P16-07, P16-08 |
| D09 | P16-04, P16-05 |
