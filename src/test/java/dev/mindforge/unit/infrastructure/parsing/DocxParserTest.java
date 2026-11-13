package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.parsing.DocxParser;
import dev.mindforge.infrastructure.parsing.ImageExtractor;

class DocxParserTest {

    private final DocxParser parser = new DocxParser();

    @Test
    void shouldReadHeadingsParagraphsAndTablesInDocumentOrder() throws IOException {
        ParsedDocument parsed = parser.parse(makeDocx());

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.heading("Mitosis", 1, 0),
            ContentBlock.text("Before the table.", 1),
            ContentBlock.text("Phase | Description\nProphase | Condensation", 2),
            ContentBlock.heading("Phases", 2, 3),
            ContentBlock.text("After the table.", 4));
        assertThat(parsed.text())
            .isEqualTo("Mitosis\n\nBefore the table.\n\nPhase | Description\nProphase | Condensation\n\nPhases\n\nAfter the table.");
        assertThat(parsed.metadata()).isEmpty();
    }

    @Test
    void shouldEmitParagraphPictureRightAfterTheParagraphText() throws IOException {
        byte[] png = makePng(Color.RED, 64);
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Intro.");
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("A figure:");
            addPicture(paragraph.createRun(), png, Document.PICTURE_TYPE_PNG);
            document.createParagraph().createRun().setText("Outro.");
            docx = write(document);
        }

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.text("Intro.", 0),
            ContentBlock.text("A figure:", 1),
            ContentBlock.image(new ImageInput(png, "image/png"), 2),
            ContentBlock.text("Outro.", 3));
        assertThat(parsed.text()).isEqualTo("Intro.\n\nA figure:\n\nOutro.");
    }

    @Test
    void shouldEmitOnlyAnImageBlockForAPictureOnlyParagraph() throws IOException {
        byte[] png = makePng(Color.RED, 64);
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Before.");
            addPicture(document.createParagraph().createRun(), png, Document.PICTURE_TYPE_PNG);
            document.createParagraph().createRun().setText("After.");
            docx = write(document);
        }

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.text("Before.", 0),
            ContentBlock.image(new ImageInput(png, "image/png"), 1),
            ContentBlock.text("After.", 2));
        assertThat(parsed.text()).isEqualTo("Before.\n\nAfter.");
    }

    @Test
    void shouldEmitTableCellPictureRightAfterTheTable() throws IOException {
        byte[] png = makePng(Color.RED, 64);
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Before.");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Cell");
            addPicture(table.getRow(0).getCell(1).getParagraphs().get(0).createRun(), png, Document.PICTURE_TYPE_PNG);
            document.createParagraph().createRun().setText("After.");
            docx = write(document);
        }

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.text("Before.", 0),
            ContentBlock.text("Cell | ", 1),
            ContentBlock.image(new ImageInput(png, "image/png"), 2),
            ContentBlock.text("After.", 3));
    }

    @Test
    void shouldKeepRunOrderForTwoPicturesInOneParagraph() throws IOException {
        byte[] first = makePng(Color.RED, 64);
        byte[] second = makePng(Color.BLUE, 64);
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            addPicture(paragraph.createRun(), first, Document.PICTURE_TYPE_PNG);
            addPicture(paragraph.createRun(), second, Document.PICTURE_TYPE_PNG);
            docx = write(document);
        }

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.image(new ImageInput(first, "image/png"), 0),
            ContentBlock.image(new ImageInput(second, "image/png"), 1));
        assertThat(parsed.text()).isEmpty();
    }

    @Test
    void shouldEmitPngByteIdenticalAndSkipEmf() throws IOException {
        byte[] png = makePng(Color.RED, 64);
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Figures.");
            addPicture(paragraph.createRun(), "not a real metafile".getBytes(), Document.PICTURE_TYPE_EMF);
            addPicture(paragraph.createRun(), png, Document.PICTURE_TYPE_PNG);
            docx = write(document);
        }

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.text("Figures.", 0),
            ContentBlock.image(new ImageInput(png, "image/png"), 1));
        assertThat(parsed.blocks().get(1).image().data()).isEqualTo(png);
    }

    @Test
    void shouldEmitARepeatedPictureOnceAndSkipASmallPicture() throws IOException {
        byte[] png = makePng(Color.RED, 64);
        byte[] small = makePng(Color.BLUE, 63);
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            addPicture(document.createParagraph().createRun(), png, Document.PICTURE_TYPE_PNG);
            addPicture(document.createParagraph().createRun(), small, Document.PICTURE_TYPE_PNG);
            document.createParagraph().createRun().setText("Between.");
            addPicture(document.createParagraph().createRun(), png, Document.PICTURE_TYPE_PNG);
            docx = write(document);
        }

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.image(new ImageInput(png, "image/png"), 0),
            ContentBlock.text("Between.", 1));
    }

    @Test
    void shouldRejectADocxWithMoreThanOneHundredDistinctPictures() throws IOException {
        byte[] docx = makeDocxWithDistinctPictures(ImageExtractor.MAX_IMAGES_PER_DOCUMENT + 1);

        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> parser.parse(docx));
    }

    @Test
    void shouldAcceptADocxWithOneHundredDistinctPictures() throws IOException {
        ParsedDocument parsed = parser.parse(makeDocxWithDistinctPictures(ImageExtractor.MAX_IMAGES_PER_DOCUMENT));

        assertThat(parsed.blocks()).hasSize(ImageExtractor.MAX_IMAGES_PER_DOCUMENT)
            .allMatch(block -> block.blockType() == BlockType.IMAGE);
        assertThat(parsed.blocks()).extracting(ContentBlock::position)
            .containsExactlyElementsOf(IntStream.range(0, ImageExtractor.MAX_IMAGES_PER_DOCUMENT)
                .boxed().toList());
    }

    @Test
    void shouldRejectBytesThatAreNotADocx() {
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> parser.parse("not a docx".getBytes()));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Heading styles carry a localized id (Polish Word writes {@code Nagwek1}) but a fixed name. */
    private static byte[] makeDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFStyles styles = docx.createStyles();
            addStyle(styles, "Nagwek1", "heading 1");
            addStyle(styles, "Nagwek2", "heading 2");

            docx.createParagraph().setStyle("Nagwek1");
            docx.getLastParagraph().createRun().setText("Mitosis");
            docx.createParagraph().createRun().setText("Before the table.");
            XWPFTable table = docx.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Phase");
            table.getRow(0).getCell(1).setText("Description");
            table.getRow(1).getCell(0).setText("Prophase");
            table.getRow(1).getCell(1).setText("Condensation");
            docx.createParagraph();
            docx.createParagraph().setStyle("Nagwek2");
            docx.getLastParagraph().createRun().setText("Phases");
            docx.createParagraph().createRun().setText("After the table.");

            docx.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] makeDocxWithDistinctPictures(int count) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            for (int i = 0; i < count; i++) {
                addPicture(document.createParagraph().createRun(), makePng(new Color(i, 0, 0), 64),
                    Document.PICTURE_TYPE_PNG);
            }
            return write(document);
        }
    }

    private static byte[] makePng(Color color, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void addPicture(XWPFRun run, byte[] bytes, int pictureType) throws IOException {
        try {
            run.addPicture(new ByteArrayInputStream(bytes), pictureType, "picture", Units.toEMU(64), Units.toEMU(64));
        } catch (InvalidFormatException e) {
            throw new IOException(e);
        }
    }

    private static byte[] write(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            return out.toByteArray();
        }
    }

    private static void addStyle(XWPFStyles styles, String id, String name) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId(id);
        style.addNewName().setVal(name);
        styles.addStyle(new XWPFStyle(style));
    }
}
