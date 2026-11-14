package dev.mindforge.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

import dev.mindforge.domain.model.ContentBlock;

/**
 * Converts a fetched HTML page into an {@link ArticleText} (ADR 0026). Boilerplate elements are removed before the
 * body is walked; {@code <h1>}–{@code <h6>} become HEADING blocks with their level, {@code <pre>} becomes a CODE
 * block, and the text of every other block element becomes a TEXT block. Output is plain text only: no markup is
 * ever read with {@code html()}. Not a document parser — HTML is not an upload format.
 */
class HtmlArticleConverter {

    private static final String BOILERPLATE = "script, style, noscript, nav, header, footer, aside, template, iframe";
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    ArticleText convert(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        document.select(BOILERPLATE).remove();
        return new ArticleText(title(document), blocks(document.body()));
    }

    private static String title(Document document) {
        String title = normalise(document.title());
        if (!title.isEmpty()) {
            return title;
        }
        Element ogTitle = document.selectFirst("meta[property=og:title]");
        if (ogTitle != null && !normalise(ogTitle.attr("content")).isEmpty()) {
            return normalise(ogTitle.attr("content"));
        }
        Element h1 = document.body().selectFirst("h1");
        if (h1 != null && !normalise(h1.text()).isEmpty()) {
            return normalise(h1.text());
        }
        return null;
    }

    private static List<ContentBlock> blocks(Element body) {
        BlockCollector collector = new BlockCollector();
        NodeTraversor.filter(collector, body);
        collector.flush();
        return collector.blocks;
    }

    private static String normalise(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    private static int headingLevel(Element element) {
        String name = element.normalName();
        if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6') {
            return name.charAt(1) - '0';
        }
        return 0;
    }

    /** Walks the body in document order, gathering inline text until a block boundary flushes it as a TEXT block. */
    private static final class BlockCollector implements NodeFilter {

        private final List<ContentBlock> blocks = new ArrayList<>();
        private final StringBuilder inline = new StringBuilder();

        @Override
        public FilterResult head(Node node, int depth) {
            if (node instanceof TextNode textNode) {
                inline.append(textNode.getWholeText());
                return FilterResult.CONTINUE;
            }
            if (!(node instanceof Element element)) {
                return FilterResult.CONTINUE;
            }
            int level = headingLevel(element);
            if (level > 0) {
                flush();
                add(ContentBlock.heading(normalise(element.text()), level, blocks.size()));
                return FilterResult.SKIP_ENTIRELY;
            }
            if (element.normalName().equals("pre")) {
                flush();
                String code = element.wholeText();
                if (!code.isBlank()) {
                    blocks.add(ContentBlock.code(code, blocks.size()));
                }
                return FilterResult.SKIP_ENTIRELY;
            }
            if (element.normalName().equals("br")) {
                inline.append(' ');
            } else if (element.isBlock()) {
                flush();
            }
            return FilterResult.CONTINUE;
        }

        @Override
        public FilterResult tail(Node node, int depth) {
            if (node instanceof Element element && element.isBlock()) {
                flush();
            }
            return FilterResult.CONTINUE;
        }

        void flush() {
            add(ContentBlock.text(normalise(inline.toString()), blocks.size()));
            inline.setLength(0);
        }

        private void add(ContentBlock block) {
            if (!block.content().isEmpty()) {
                blocks.add(block);
            }
        }
    }
}
