package dev.mindforge.domain.model;

import java.util.Map;

/**
 * A single positioned unit of parsed document content (a heading, paragraph, code block,
 * image reference, etc.). Immutable; the metadata map is defensively copied.
 */
public record ContentBlock(
    BlockType blockType,
    String content,
    String mediaRef,
    String mediaType,
    Map<String, Object> metadata,
    int position
) {

    /** Metadata key holding a {@link BlockType#HEADING}'s level, 1 for the outermost. */
    public static final String LEVEL = "level";

    public ContentBlock {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ContentBlock heading(String text, int level, int position) {
        return new ContentBlock(BlockType.HEADING, text, null, null, Map.of(LEVEL, level), position);
    }

    public static ContentBlock text(String text, int position) {
        return new ContentBlock(BlockType.TEXT, text, null, null, null, position);
    }

    public static ContentBlock code(String code, int position) {
        return new ContentBlock(BlockType.CODE, code, null, null, null, position);
    }
}
