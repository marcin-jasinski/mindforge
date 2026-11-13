package dev.mindforge.domain.model;

import java.util.Map;

/**
 * A single positioned unit of parsed document content (a heading, paragraph, code block, image, etc.). Immutable;
 * the metadata map is defensively copied. An {@link BlockType#IMAGE} block carries its bytes in {@code image} and has
 * empty {@code content}; every other block type has {@code image == null}.
 */
public record ContentBlock(
    BlockType blockType,
    String content,
    String mediaRef,
    String mediaType,
    Map<String, Object> metadata,
    int position,
    ImageInput image
) {

    /** Metadata key holding a {@link BlockType#HEADING}'s level, 1 for the outermost. */
    public static final String LEVEL = "level";

    public ContentBlock {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if ((blockType == BlockType.IMAGE) != (image != null)) {
            throw new IllegalArgumentException("An image is required for an IMAGE block and only for it: " + blockType);
        }
    }

    public static ContentBlock heading(String text, int level, int position) {
        return new ContentBlock(BlockType.HEADING, text, null, null, Map.of(LEVEL, level), position, null);
    }

    public static ContentBlock text(String text, int position) {
        return new ContentBlock(BlockType.TEXT, text, null, null, null, position, null);
    }

    public static ContentBlock code(String code, int position) {
        return new ContentBlock(BlockType.CODE, code, null, null, null, position, null);
    }

    public static ContentBlock image(ImageInput image, int position) {
        return new ContentBlock(BlockType.IMAGE, "", null, null, null, position, image);
    }
}
