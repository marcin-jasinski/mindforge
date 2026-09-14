package dev.mindforge.application.ingest;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.TextRules;

/**
 * Cleans parsed blocks before Extract: headings on one line, line endings and trailing whitespace normalised, runs
 * of blank lines collapsed outside code, and blocks left without text dropped. Positions are kept, so claims still
 * point at the parser's blocks. Plain code: no model, no {@code VERSION}.
 */
public final class Preprocessor {

    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);
    private static final Pattern BLANK_LINE_RUN = Pattern.compile("\\n{3,}");

    private Preprocessor() {}

    public static List<ContentBlock> clean(List<ContentBlock> blocks) {
        return blocks.stream()
            .map(block -> new ContentBlock(block.blockType(), clean(block), block.mediaRef(), block.mediaType(),
                block.metadata(), block.position()))
            .filter(block -> !block.content().isBlank())
            .toList();
    }

    private static String clean(ContentBlock block) {
        String content = Objects.requireNonNullElse(block.content(), "").replace("\r\n", "\n");
        if (block.blockType() == BlockType.HEADING) {
            return TextRules.singleLine(content);
        }
        String trimmed = TRAILING_WHITESPACE.matcher(content).replaceAll("");
        return block.blockType() == BlockType.CODE
            ? trimmed.strip()
            : BLANK_LINE_RUN.matcher(trimmed).replaceAll("\n\n").strip();
    }
}
