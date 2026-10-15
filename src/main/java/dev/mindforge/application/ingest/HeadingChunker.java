package dev.mindforge.application.ingest;

import java.util.ArrayList;
import java.util.List;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.TokenEstimate;

/**
 * Splits a document's blocks into chunks for Extract, without overlap. A section — a heading and the
 * blocks up to the next heading, of any level — starts a new chunk when it does not fit the current
 * one; a section larger than a whole chunk is split at block boundaries.
 */
public final class HeadingChunker {

    private HeadingChunker() {}

    /**
     * Chunks of at most {@code maxTokens} by {@link TokenEstimate}, in document order. Blocks are never
     * cut, so a single block larger than {@code maxTokens} is a chunk of its own.
     */
    public static List<List<ContentBlock>> split(List<ContentBlock> blocks, int maxTokens) {
        List<List<ContentBlock>> chunks = new ArrayList<>();
        List<ContentBlock> current = new ArrayList<>();
        int currentTokens = 0;
        for (List<ContentBlock> section : sections(blocks)) {
            if (currentTokens + tokens(section) > maxTokens) {
                flush(current, chunks);
                currentTokens = 0;
            }
            for (ContentBlock block : section) {
                int blockTokens = TokenEstimate.of(block.content());
                if (currentTokens + blockTokens > maxTokens) {
                    flush(current, chunks);
                    currentTokens = 0;
                }
                current.add(block);
                currentTokens += blockTokens;
            }
        }
        flush(current, chunks);
        return chunks;
    }

    private static List<List<ContentBlock>> sections(List<ContentBlock> blocks) {
        List<List<ContentBlock>> sections = new ArrayList<>();
        for (ContentBlock block : blocks) {
            if (sections.isEmpty() || block.blockType() == BlockType.HEADING) {
                sections.add(new ArrayList<>());
            }
            sections.getLast().add(block);
        }
        return sections;
    }

    private static int tokens(List<ContentBlock> section) {
        return section.stream().mapToInt(block -> TokenEstimate.of(block.content())).sum();
    }

    private static void flush(List<ContentBlock> current, List<List<ContentBlock>> chunks) {
        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
            current.clear();
        }
    }
}
