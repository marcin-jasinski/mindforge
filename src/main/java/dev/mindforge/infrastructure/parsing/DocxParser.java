package dev.mindforge.infrastructure.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/**
 * A DOCX body in document order: paragraphs, with {@code heading N} styles as headings, and tables as
 * text where the prose places them. Headings are matched by style name, which Word keeps in English,
 * rather than by style id, which it localizes. The accepted pictures in a paragraph's or table's runs follow its
 * block, in run order; each picture part is considered once, at its first use.
 */
public class DocxParser implements FormatParser {

    public static final String MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Pattern HEADING_STYLE = Pattern.compile("heading ([1-9])", Pattern.CASE_INSENSITIVE);
    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String CELL_SEPARATOR = " | ";
    private static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

    @Override
    public ParsedDocument parse(byte[] content) {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            Pictures pictures = new Pictures();
            List<ContentBlock> blocks = new ArrayList<>();
            for (IBodyElement element : docx.getBodyElements()) {
                switch (element) {
                    case XWPFParagraph paragraph -> {
                        addIfNotBlank(blocks, paragraph(docx, paragraph, blocks.size()));
                        pictures.addImages(blocks, paragraph.getRuns());
                    }
                    case XWPFTable table -> {
                        addIfNotBlank(blocks, ContentBlock.text(tableText(table), blocks.size()));
                        pictures.addImages(blocks, tableRuns(table));
                    }
                    default -> { }
                }
            }
            return new ParsedDocument(
                blocks.stream()
                    .filter(block -> block.blockType() != BlockType.IMAGE)
                    .map(ContentBlock::content)
                    .collect(Collectors.joining(BLOCK_SEPARATOR)),
                blocks,
                Map.of());
        } catch (IOException | POIXMLException | UnsupportedFileFormatException e) {
            throw new UploadRejectedException("The document is not a readable DOCX file", e);
        }
    }

    private static void addIfNotBlank(List<ContentBlock> blocks, ContentBlock block) {
        if (!block.content().isBlank()) {
            blocks.add(block);
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

    private static List<XWPFRun> tableRuns(XWPFTable table) {
        List<XWPFRun> runs = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    runs.addAll(paragraph.getRuns());
                }
            }
        }
        return runs;
    }

    /** The document's picture parts already considered, and the images accepted from them so far. */
    private static final class Pictures {

        private final ImageExtractor extractor = new ImageExtractor();
        private final Set<String> processedParts = new HashSet<>();
        private int images;

        void addImages(List<ContentBlock> blocks, List<XWPFRun> runs) {
            for (XWPFRun run : runs) {
                for (XWPFPicture picture : run.getEmbeddedPictures()) {
                    XWPFPictureData data = picture.getPictureData();
                    if (data == null || !processedParts.add(data.getPackagePart().getPartName().getName())) {
                        continue;
                    }
                    Optional<ImageInput> image = extractor.accept(data.getData(), mimeType(data));
                    if (image.isPresent()) {
                        blocks.add(ContentBlock.image(image.get(), blocks.size()));
                        images++;
                        extractor.checkCount(images);
                    }
                }
            }
        }

        private static String mimeType(XWPFPictureData data) {
            PictureType type = data.getPictureTypeEnum();
            return type == null ? UNKNOWN_MIME_TYPE : type.getContentType();
        }
    }
}
