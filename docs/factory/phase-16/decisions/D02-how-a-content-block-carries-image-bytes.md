---
id: D02
question: Q02
status: decided
method: majority
class: local
votes: { pragmatist: B, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q02.r1.tally.md]
adr: 
---
## Decision
Use A: add a nullable seventh component `ImageInput image` (the D03 record) as the last component of `ContentBlock`. It is set only for `BlockType.IMAGE` and is `null` for every other block type. The MIME type lives only in `image.mimeType()`; for IMAGE blocks `content` is `""` and `mediaRef` and `mediaType` stay `null`. Jackson writes the field into the existing `content_blocks` JSONB as `{"data": "<base64>", "mimeType": "..."}`; old rows without the field read back with `image == null`. No migration.

## Why
- ADR 0021 (Accepted) Consequences: "an image carried in a document block reuses it" (`ImageInput`); D03 says the same. B and a raw `byte[]` both break that.
- `ImageInput` already gives content `equals`/`hashCode`, a defensive copy and a byte-free `toString` (D03 notes), so the record-array and log-leak problems in the question do not occur. `ContentBlock` has no `toString` override (`ContentBlock.java:9-36`), so B would print the base64.
- `ImageDescriber` passes `block.image()` straight to the D03 gateway overload: no base64 decode in the domain, and invalid bytes fail at parse time, not inside the retry/breaker wrap.
- Churn is small: 4 `new ContentBlock(` sites in `src/main`, 0 in `src/test` (grep).
- C puts bytes on the text path (`Preprocessor.java:27` keeps non-blank content).
- Class `local`: the persisted-shape decision (bytes in `content_blocks`) is ADR 0020, the type is ADR 0021; this is the shape both already require.

## Rejected
- B: carries an image without `ImageInput` (against ADR 0021); base64 in a reference-named field; generated `toString` dumps megabytes.
- C: bytes read as text by Preprocess, chunking and token estimates if an IMAGE block is ever undescribed.
- Raw `byte[] data` component: identity equals on the record, and a second image representation against ADR 0021.

## Dissent
Pragmatist (B): no signature change and no new nested JSON shape. Risk to watch: Jackson round-trip of `ImageInput` (canonical constructor validation, cloning accessor) and of old rows without `image`; prove both with tests.

## Implementation notes
- `src/main/java/dev/mindforge/domain/model/ContentBlock.java`: components `(BlockType blockType, String content, String mediaRef, String mediaType, Map<String, Object> metadata, int position, ImageInput image)`. Compact constructor: keep the metadata copy; enforce `(blockType == BlockType.IMAGE) == (image != null)` with `IllegalArgumentException`. Update the class Javadoc ("image reference" -> image bytes in `image`).
- Add `public static ContentBlock image(ImageInput image, int position)` -> `new ContentBlock(BlockType.IMAGE, "", null, null, null, position, image)`. Parsers (D09) use only this factory.
- Update `heading`, `text`, `code` factories to pass `null`. Do not add a 6-arg convenience constructor (only 4 sites; minimal-implementation).
- `Preprocessor.java:25`: pass `block.image()` through. IMAGE blocks must not reach `clean` (D01: describe first); do not add special handling there beyond the pass-through.
- `ImageInput` comes from D03 (`dev.mindforge.domain.model.ImageInput`); if it is not yet written when this ticket starts, write it per D03 notes. Do not add a second image type.
- Tests: `ContentBlockTest` — IMAGE without image and TEXT with image are rejected; `image(...)` sets content `""`; two IMAGE blocks with equal bytes are `equals`; `toString` has no base64. Persistence (Testcontainers, `DocumentRepository` adapter): an IMAGE block round-trips with equal bytes and MIME type; a row whose JSONB lacks the `image` key (insert raw JSON, old shape) reads back with `image() == null`. `PreprocessorTest`: `image` survives a rebuild of a non-IMAGE block as `null`.
