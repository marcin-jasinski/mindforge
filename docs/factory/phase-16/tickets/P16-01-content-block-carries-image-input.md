---
id: P16-01
title: Add the ImageInput value and let an IMAGE content block carry it
status: ready
plan_tasks: [16.1]
decisions: [D02, D03]
depends_on: []
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
A domain value `ImageInput(byte[] data, String mimeType)` exists, and a `ContentBlock` of type `IMAGE` carries one in
a new last component `image`. IMAGE blocks persist in the existing `documents.content_blocks` JSONB and read back with
equal bytes; rows written before this change still read (with `image() == null`). No migration.

## Context
- Phase 16 (Image Analysis) describes images from uploaded PDF/DOCX with the `VISION` tier. This ticket is the
  domain foundation; parsers (P16-04/05), the gateway overload (P16-06) and `ImageDescriber` (P16-07) use it.
- `src/main/java/dev/mindforge/domain/model/ContentBlock.java` today: record
  `(BlockType blockType, String content, String mediaRef, String mediaType, Map<String,Object> metadata, int position)`,
  compact constructor copies metadata, factories `heading`, `text`, `code`. Javadoc mentions "image reference".
- `BlockType.IMAGE` already exists (`domain/model/BlockType.java`).
- The only other `new ContentBlock(` call in `src/main` is `src/main/java/dev/mindforge/application/ingest/Preprocessor.java:25`
  (rebuilds each block with cleaned content). There are none in `src/test`.
- `DocumentEntity` (`infrastructure/persistence/entity/DocumentEntity.java:62-64`) maps `List<ContentBlock>` to the
  `content_blocks` jsonb column with `@JdbcTypeCode(SqlTypes.JSON)`; Jackson (de)serializes the domain record directly.
- Domain package is JDK only (CLAUDE.md, `docs/standards/architecture/hexagonal.md`). Records for value objects;
  `IllegalArgumentException` for an invalid value is the existing pattern for record validation.
- ADR 0021 (`docs/adr/0021-aigateway-image-overload.md`): `ImageInput` is the only domain image value; an image in a
  document block reuses it; the record stays two fields.
- ADR 0020: image bytes are stored in `content_blocks`.

## Decisions to apply
**D02 — Decision:** add a nullable seventh component `ImageInput image` (the D03 record) as the last component of
`ContentBlock`. It is set only for `BlockType.IMAGE` and is `null` for every other block type. The MIME type lives only
in `image.mimeType()`; for IMAGE blocks `content` is `""` and `mediaRef` and `mediaType` stay `null`. Jackson writes the
field into the existing `content_blocks` JSONB as `{"data": "<base64>", "mimeType": "..."}`; old rows without the
field read back with `image == null`. No migration.

**D02 — Implementation notes:**
- `ContentBlock.java`: components `(BlockType blockType, String content, String mediaRef, String mediaType,
  Map<String, Object> metadata, int position, ImageInput image)`. Compact constructor: keep the metadata copy; enforce
  `(blockType == BlockType.IMAGE) == (image != null)` with `IllegalArgumentException`. Update the class Javadoc
  ("image reference" -> image bytes in `image`).
- Add `public static ContentBlock image(ImageInput image, int position)` ->
  `new ContentBlock(BlockType.IMAGE, "", null, null, null, position, image)`. Parsers use only this factory.
- Update `heading`, `text`, `code` factories to pass `null`. Do not add a 6-arg convenience constructor.
- `Preprocessor.java:25`: pass `block.image()` through. IMAGE blocks must not reach `clean` (they are described
  first, P16-08); do not add special handling there beyond the pass-through.
- Do not add a second image type.
- Tests: `ContentBlockTest` — IMAGE without image and TEXT with image are rejected; `image(...)` sets content `""`;
  two IMAGE blocks with equal bytes are `equals`; `toString` has no base64. Persistence (Testcontainers,
  `DocumentRepository` adapter): an IMAGE block round-trips with equal bytes and MIME type; a row whose JSONB lacks the
  `image` key (insert raw JSON, old shape) reads back with `image() == null`. `PreprocessorTest`: `image` survives a
  rebuild of a non-IMAGE block as `null`.
- Dissent risk to prove with tests: Jackson round-trip of `ImageInput` (canonical constructor validation, cloning
  accessor) and of old rows without `image`.

**D03 (the `ImageInput` part only) — Implementation notes:**
- `src/main/java/dev/mindforge/domain/model/ImageInput.java`: `public record ImageInput(byte[] data, String mimeType)`.
  Compact constructor: `Objects.requireNonNull` both; reject `data.length == 0`; reject a `mimeType` that does not
  start with `image/` (`IllegalArgumentException`); store `data.clone()`. Accessor `data()` returns a clone. Override
  `equals`/`hashCode` with `Arrays.equals`/`Arrays.hashCode` and `toString` as
  `ImageInput[mimeType=..., bytes=<length>]` — never the bytes. JDK only. Exactly two fields.
- `ImageInputTest`: rejects null/empty data, non-`image/` MIME; defensive copy (mutating the input array or the
  returned array does not change the record); `toString` has no bytes.

## Acceptance criteria
- [ ] AC1: `ImageInput` exists in `dev.mindforge.domain.model`, imports only JDK classes, validates as above, copies
  bytes in and out, compares by content, and its `toString` shows MIME type and byte length only.
- [ ] AC2: `ContentBlock` has the seventh component `image`; constructing IMAGE without `image`, or any other type with
  `image`, throws `IllegalArgumentException`.
- [ ] AC3: `ContentBlock.image(img, pos)` returns an IMAGE block with `content == ""`, `mediaRef == null`,
  `mediaType == null`, the given position and image; `heading`/`text`/`code` produce `image() == null`.
- [ ] AC4: `ContentBlock.toString()` of an IMAGE block contains no base64 or raw bytes.
- [ ] AC5: `Preprocessor.clean` keeps the `image` component on rebuilt blocks (null for non-IMAGE blocks).
- [ ] AC6: A `Document` with an IMAGE block inserted through `DocumentRepositoryAdapter` reads back with equal
  `ImageInput` (bytes and MIME type).
- [ ] AC7: A `documents` row whose `content_blocks` JSON lacks the `image` key (old shape, inserted with raw SQL) reads
  back with every block's `image() == null`.
- [ ] AC8: No Flyway migration is added; all existing tests still pass.

## Test plan
- Unit (plain JUnit + AssertJ, no Spring): `src/test/java/dev/mindforge/unit/domain/ImageInputTest.java` (new) for AC1;
  `src/test/java/dev/mindforge/unit/domain/ContentBlockTest.java` (new) for AC2–AC4;
  `src/test/java/dev/mindforge/unit/application/PreprocessorTest.java` (new) for AC5.
- Integration (Testcontainers PostgreSQL): extend
  `src/test/java/dev/mindforge/integration/persistence/DocumentRepositoryAdapterTest.java` for AC6–AC7.
- Use `make*` static factories for test objects (e.g. `makeImage()`), not `@BeforeEach` fixtures.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/ImageInput.java` (new)
- `src/main/java/dev/mindforge/domain/model/ContentBlock.java`
- `src/main/java/dev/mindforge/application/ingest/Preprocessor.java`
- `src/test/java/dev/mindforge/unit/domain/ImageInputTest.java` (new)
- `src/test/java/dev/mindforge/unit/domain/ContentBlockTest.java` (new)
- `src/test/java/dev/mindforge/unit/application/PreprocessorTest.java` (new)
- `src/test/java/dev/mindforge/integration/persistence/DocumentRepositoryAdapterTest.java`

## Out of scope
- The `AIGateway` overload (P16-06), parsers (P16-04/05), any read-path change for the document list (P16-02).
- Fields on `ImageInput` beyond `data` and `mimeType`; AUDIO/VIDEO support; a migration.
- Special IMAGE handling inside `Preprocessor.clean` beyond passing `image` through.

## Notes
