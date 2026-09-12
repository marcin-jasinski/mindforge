package dev.mindforge.infrastructure.parsing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterBlock;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;

/**
 * Markdown as its top-level blocks: headings with their level, code blocks, and every other block as
 * its Markdown source. YAML frontmatter becomes metadata — the first value of each key, unquoted.
 */
public class MarkdownParser implements FormatParser {

    public static final String MIME_TYPE = "text/markdown";

    private final Parser markdown = Parser.builder(
        new MutableDataSet().set(Parser.EXTENSIONS, List.of(YamlFrontMatterExtension.create()))).build();

    @Override
    public ParsedDocument parse(byte[] content) {
        String text = PlainTextParser.decodeUtf8(content);
        Node document = markdown.parse(text);
        List<ContentBlock> blocks = new ArrayList<>();
        for (Node node : document.getChildren()) {
            int position = blocks.size();
            ContentBlock block = switch (node) {
                case YamlFrontMatterBlock frontmatter -> null;
                case Heading heading ->
                    ContentBlock.heading(heading.getText().toString().strip(), heading.getLevel(), position);
                case FencedCodeBlock code -> ContentBlock.code(code.getContentChars().toString().stripTrailing(), position);
                case IndentedCodeBlock code -> ContentBlock.code(code.getContentChars().toString().stripTrailing(), position);
                default -> ContentBlock.text(node.getChars().toString().strip(), position);
            };
            if (block != null && !block.content().isBlank()) {
                blocks.add(block);
            }
        }
        return new ParsedDocument(text, blocks, frontmatter(document));
    }

    private static Map<String, String> frontmatter(Node document) {
        AbstractYamlFrontMatterVisitor visitor = new AbstractYamlFrontMatterVisitor();
        visitor.visit(document);
        Map<String, String> metadata = new LinkedHashMap<>();
        visitor.getData().forEach((key, values) -> {
            if (!values.isEmpty()) {
                metadata.put(key, unquote(values.getFirst()));
            }
        });
        return metadata;
    }

    private static String unquote(String value) {
        String stripped = value.strip();
        boolean quoted = stripped.length() >= 2
            && (stripped.charAt(0) == '"' || stripped.charAt(0) == '\'')
            && stripped.charAt(stripped.length() - 1) == stripped.charAt(0);
        return quoted ? stripped.substring(1, stripped.length() - 1) : stripped;
    }
}
