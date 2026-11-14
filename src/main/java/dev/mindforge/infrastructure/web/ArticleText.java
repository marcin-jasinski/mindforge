package dev.mindforge.infrastructure.web;

import java.util.List;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.ContentBlock;

/**
 * The plain-text result of converting a fetched HTML page: its title ({@code null} when the page has none) and its
 * HEADING, TEXT and CODE blocks in document order. The block list is defensively copied.
 */
record ArticleText(String title, List<ContentBlock> blocks) {

    ArticleText {
        blocks = List.copyOf(blocks);
    }

    /** The blocks' content joined with a blank line, in order; empty when there are no blocks. */
    String text() {
        return blocks.stream().map(ContentBlock::content).collect(Collectors.joining("\n\n"));
    }
}
