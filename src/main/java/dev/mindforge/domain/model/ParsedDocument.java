package dev.mindforge.domain.model;

import java.util.List;
import java.util.Map;

/**
 * What a parser read from one uploaded document: its text, its blocks in document order, and the
 * metadata {@link LessonIdentity#resolve} looks at (frontmatter keys, a PDF {@code Title}).
 */
public record ParsedDocument(String text, List<ContentBlock> blocks, Map<String, String> metadata) {

    public ParsedDocument {
        blocks = List.copyOf(blocks);
        metadata = Map.copyOf(metadata);
    }
}
