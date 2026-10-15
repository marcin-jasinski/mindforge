package dev.mindforge.infrastructure.parsing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.commonmark.ext.front.matter.YamlFrontMatterBlock;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;

/**
 * Markdown as its top-level blocks, parsed by commonmark-java: headings with their level, code blocks, and every other
 * block as its Markdown source. YAML frontmatter becomes metadata — the first value of each key.
 */
public class MarkdownParser implements FormatParser {

    public static final String MIME_TYPE = "text/markdown";

    private final Parser markdown = Parser.builder()
        .extensions(List.of(YamlFrontMatterExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS)
        .build();
    private final TextContentRenderer plainText = TextContentRenderer.builder().build();

    @Override
    public ParsedDocument parse(byte[] content) {
        String text = PlainTextParser.decodeUtf8(content);
        Node document = markdown.parse(text);
        List<ContentBlock> blocks = new ArrayList<>();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            int position = blocks.size();
            ContentBlock block = switch (node) {
                case YamlFrontMatterBlock frontmatter -> null;
                case Heading heading ->
                    ContentBlock.heading(plainText.render(heading).strip(), heading.getLevel(), position);
                case FencedCodeBlock code -> ContentBlock.code(code.getLiteral().stripTrailing(), position);
                case IndentedCodeBlock code -> ContentBlock.code(code.getLiteral().stripTrailing(), position);
                default -> ContentBlock.text(source(text, node).strip(), position);
            };
            if (block != null && !block.content().isBlank()) {
                blocks.add(block);
            }
        }
        return new ParsedDocument(text, blocks, frontmatter(document));
    }

    /** The Markdown a block was parsed from: its first source line through the end of its last. */
    private static String source(String text, Node block) {
        List<SourceSpan> spans = block.getSourceSpans();
        if (spans.isEmpty()) {
            return "";
        }
        SourceSpan last = spans.getLast();
        return text.substring(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }

    private static Map<String, String> frontmatter(Node document) {
        Map<String, String> metadata = new LinkedHashMap<>();
        YamlFrontMatterVisitor.readData(document).forEach((key, values) -> {
            if (!values.isEmpty()) {
                metadata.put(key, values.getFirst());
            }
        });
        return metadata;
    }
}
