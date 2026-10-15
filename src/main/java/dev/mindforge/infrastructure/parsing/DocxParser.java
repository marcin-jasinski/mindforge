package dev.mindforge.infrastructure.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/**
 * A DOCX body in document order: paragraphs, with {@code heading N} styles as headings, and tables as
 * text where the prose places them. Headings are matched by style name, which Word keeps in English,
 * rather than by style id, which it localizes.
 */
public class DocxParser implements FormatParser {

    public static final String MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Pattern HEADING_STYLE = Pattern.compile("heading ([1-9])", Pattern.CASE_INSENSITIVE);
    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String CELL_SEPARATOR = " | ";

    @Override
    public ParsedDocument parse(byte[] content) {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<ContentBlock> blocks = new ArrayList<>();
            for (IBodyElement element : docx.getBodyElements()) {
                int position = blocks.size();
                ContentBlock block = switch (element) {
                    case XWPFParagraph paragraph -> paragraph(docx, paragraph, position);
                    case XWPFTable table -> ContentBlock.text(tableText(table), position);
                    default -> null;
                };
                if (block != null && !block.content().isBlank()) {
                    blocks.add(block);
                }
            }
            return new ParsedDocument(
                blocks.stream().map(ContentBlock::content).collect(Collectors.joining(BLOCK_SEPARATOR)),
                blocks,
                Map.of());
        } catch (IOException | POIXMLException | UnsupportedFileFormatException e) {
            throw new UploadRejectedException("The document is not a readable DOCX file", e);
        }
    }

    private static ContentBlock paragraph(XWPFDocument docx, XWPFParagraph paragraph, int position) {
        String text = paragraph.getText().strip();
        Matcher heading = HEADING_STYLE.matcher(styleName(docx, paragraph));
        return heading.matches()
            ? ContentBlock.heading(text, Integer.parseInt(heading.group(1)), position)
            : ContentBlock.text(text, position);
    }

    private static String styleName(XWPFDocument docx, XWPFParagraph paragraph) {
        if (paragraph.getStyleID() == null || docx.getStyles() == null) {
            return "";
        }
        XWPFStyle style = docx.getStyles().getStyle(paragraph.getStyleID());
        return style == null || style.getName() == null ? "" : style.getName();
    }

    private static String tableText(XWPFTable table) {
        return table.getRows().stream()
            .map(row -> row.getTableCells().stream()
                .map(cell -> cell.getText().strip())
                .collect(Collectors.joining(CELL_SEPARATOR)))
            .collect(Collectors.joining("\n"));
    }
}
