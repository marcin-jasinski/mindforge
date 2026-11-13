package dev.mindforge.application.ingest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;

/**
 * The text an image is described against (D06): the content of the TEXT and HEADING blocks before it, in document
 * order, cut from the start to the last {@link #CONTEXT_CHARS} characters. Image blocks add nothing, so an earlier
 * image's description never enters the context. Plain code: no model, no {@code VERSION}.
 */
public final class ImageContext {

    public static final int CONTEXT_CHARS = 1500;

    private static final String SEPARATOR = "\n\n";

    private ImageContext() {}

    /** The context of the block at {@code index} of {@code blocks}; empty when no text comes before it. */
    public static String before(List<ContentBlock> blocks, int index) {
        Deque<String> parts = new ArrayDeque<>();
        int length = 0;
        for (int i = index - 1; i >= 0 && length < CONTEXT_CHARS; i--) {
            ContentBlock block = blocks.get(i);
            boolean text = block.blockType() == BlockType.TEXT || block.blockType() == BlockType.HEADING;
            if (text && block.content() != null && !block.content().isBlank()) {
                String content = block.content().strip();
                length += content.length() + (parts.isEmpty() ? 0 : SEPARATOR.length());
                parts.addFirst(content);
            }
        }
        String context = String.join(SEPARATOR, parts);
        return context.length() > CONTEXT_CHARS ? context.substring(context.length() - CONTEXT_CHARS) : context;
    }
}
