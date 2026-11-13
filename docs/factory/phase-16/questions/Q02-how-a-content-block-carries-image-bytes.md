---
id: Q02
status: decided
decision: D02
impact: medium
blocks: [W01, W02, W03]
---
## Question
How does a `ContentBlock` with `BlockType.IMAGE` carry its bytes and MIME type?

## Context
- Plan 16.1: parsers "emit `ContentBlock`s with `BlockType.IMAGE` carrying bytes + MIME type".
- `ContentBlock` (`src/main/java/dev/mindforge/domain/model/ContentBlock.java`) is a record:
  `blockType, content, mediaRef (String), mediaType (String), metadata, position`. No byte field. `mediaType` fits
  the MIME type; `mediaRef` was meant for a reference (old object-storage design, removed).
- `Preprocessor.clean` rebuilds every block through the canonical constructor and drops blocks with blank `content`.
- The record is serialized by Jackson into JSONB (`DocumentEntity`). A `byte[]` component is written as base64 by
  Jackson, but a record with an array component gets identity `equals`/`hashCode` (tests compare blocks).
- Depends on Q01: with option B the bytes never leave the upload path, and a smaller in-memory shape may do.

## Known options
- A: Add a `byte[] data` component (defensively copied), set only for IMAGE; `mediaType` holds the MIME type. Every
  `new ContentBlock(...)` call site changes; equality needs care.
- B: Keep the record shape; put base64 of the bytes in `mediaRef` (or a `data:` URI) and the MIME type in `mediaType`;
  `content` stays empty until described. No signature churn; the domain decodes base64 (JDK `Base64`).
- C: Put base64 in `content`. Then `TokenEstimate`, `HeadingChunker` and any text join would read the bytes as text
  if an IMAGE block ever reached them undescribed — risky.

## Constraints
- Domain is JDK only (`CLAUDE.md`).
- `record` for value objects; immutable with defensive copies (`docs/standards/backend/java-conventions.md`).
- Existing stored rows (no image field) must still deserialize.
