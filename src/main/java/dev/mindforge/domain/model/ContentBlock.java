package dev.mindforge.domain.model;

import java.util.Map;

/**
 * A single positioned unit of parsed document content (a paragraph, code block,
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

    public ContentBlock {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
