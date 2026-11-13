---
id: Q09
status: decided
decision: D09
impact: low
blocks: [W02, W03]
---
## Question
Where in document order does an IMAGE block sit relative to the text around it?

## Context
- Plan 16.1/16.2 say "in document order". `PdfParser` emits one TEXT block per page (`PDFTextStripper` per page);
  `DocxParser` emits one block per body element (paragraph/table). An image inside a page or a paragraph has no block
  boundary of its own.
- Claims cite block position ranges (`Claim.firstBlock/lastBlock`; `IngestPipeline.sourceBlocks`), and
  `HeadingChunker` cuts at headings; placement decides which chunk and which writer sources see the description.

## Known options
- A: PDF — image blocks after the page's text block, in content-stream order. DOCX — image block right after the
  paragraph (or table) that holds the picture. Simple; text and image of one page stay adjacent.
- B: PDF — split the page text at each image's vertical position (custom `PDFTextStripper` + `PDFStreamEngine`
  positions). Exact order; much more code.
- C: All images of the document at the end. Loses locality; contradicts "in document order".

## Constraints
Positions are consecutive and stable (`ContentBlock.position`; `Preprocessor` keeps positions).
