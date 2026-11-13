package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.parsing.PdfParser;

class PdfParserTest {

    private final PdfParser parser = new PdfParser();

    @Test
    void shouldReadEachNonBlankPageAsATextBlockAndTheTitleAsMetadata() throws IOException {
        ParsedDocument parsed = parser.parse(makePdf("Biology — lesson 3", "Cell division", "", "Mitosis phases"));

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.text("Cell division", 0),
            ContentBlock.text("Mitosis phases", 1));
        assertThat(parsed.text()).isEqualTo("Cell division\n\nMitosis phases");
        assertThat(parsed.metadata()).isEqualTo(Map.of("Title", "Biology — lesson 3"));
    }

    @Test
    void shouldHaveNoMetadataWithoutATitle() throws IOException {
        assertThat(parser.parse(makePdf(null, "Text")).metadata()).isEmpty();
    }

    @Test
    void shouldRejectBytesThatAreNotAPdf() {
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> parser.parse("not a pdf".getBytes()));
    }

    @Test
    void shouldEmitThePageTextThenItsImageAsPng() throws IOException {
        BufferedImage red = makeImage(Color.RED, 80);

        ParsedDocument parsed = parser.parse(makePdfWithImages(List.of(
            new PageSpec("Cell division", List.of(lossless(red))))));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType, ContentBlock::position)
            .containsExactly(tuple(BlockType.TEXT, 0), tuple(BlockType.IMAGE, 1));
        ImageInput image = parsed.blocks().get(1).image();
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(colorOf(image)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void shouldEmitTwoImagesOfAPageInDrawOrder() throws IOException {
        ImageSpec red = lossless(makeImage(Color.RED, 80));
        ImageSpec blue = lossless(makeImage(Color.BLUE, 80));

        ParsedDocument parsed = parser.parse(
            makePdfWithImagesInDrawOrder("Two figures", List.of(red, blue), List.of(1, 0)));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType)
            .containsExactly(BlockType.TEXT, BlockType.IMAGE, BlockType.IMAGE);
        assertThat(colorOf(parsed.blocks().get(1).image())).isEqualTo(Color.BLUE.getRGB());
        assertThat(colorOf(parsed.blocks().get(2).image())).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void shouldEmitTheImageOfAPageWithoutText() throws IOException {
        ParsedDocument parsed = parser.parse(makePdfWithImages(List.of(
            new PageSpec("", List.of(lossless(makeImage(Color.GREEN, 80)))))));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType, ContentBlock::position)
            .containsExactly(tuple(BlockType.IMAGE, 0));
        assertThat(parsed.text()).isEmpty();
    }

    @Test
    void shouldEmitTheSameImageOnThreePagesOnceAtItsFirstOccurrence() throws IOException {
        ImageSpec logo = lossless(makeImage(Color.ORANGE, 80));

        ParsedDocument parsed = parser.parse(makePdfWithImages(List.of(
            new PageSpec("Page one", List.of(logo)),
            new PageSpec("Page two", List.of(logo)),
            new PageSpec("Page three", List.of(logo)))));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType, ContentBlock::position)
            .containsExactly(
                tuple(BlockType.TEXT, 0), tuple(BlockType.IMAGE, 1),
                tuple(BlockType.TEXT, 2), tuple(BlockType.TEXT, 3));
    }

    @Test
    void shouldDecodeAnImageDrawnManyTimesOnlyOnce() throws IOException {
        ImageSpec large = lossless(makeImage(Color.ORANGE, 1500));
        List<ImageSpec> draws = Collections.nCopies(400, large);
        byte[] pdf = makePdfWithImages(List.of(new PageSpec("Logo", draws), new PageSpec("Again", draws)));

        ParsedDocument parsed = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> parser.parse(pdf));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType, ContentBlock::position)
            .containsExactly(tuple(BlockType.TEXT, 0), tuple(BlockType.IMAGE, 1), tuple(BlockType.TEXT, 2));
    }

    @Test
    void shouldEmitAJpegByteIdenticalAndALosslessImageAsDecodablePng() throws IOException {
        byte[] jpeg = makeJpeg(makeImage(Color.MAGENTA, 96));

        ParsedDocument parsed = parser.parse(makePdfWithImages(List.of(
            new PageSpec("Photo and chart", List.of(dct(jpeg, 96), lossless(makeImage(Color.CYAN, 72)))))));

        ImageInput photo = parsed.blocks().get(1).image();
        ImageInput chart = parsed.blocks().get(2).image();
        assertThat(photo.mimeType()).isEqualTo("image/jpeg");
        assertThat(photo.data()).isEqualTo(jpeg);
        assertThat(chart.mimeType()).isEqualTo("image/png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(chart.data()));
        assertThat(decoded.getWidth()).isEqualTo(72);
        assertThat(decoded.getHeight()).isEqualTo(72);
    }

    @Test
    void shouldSkipASmallImageAndAnImageThatDoesNotDecode() throws IOException {
        ParsedDocument parsed = parser.parse(makePdfWithImages(List.of(
            new PageSpec("Figures", List.of(
                lossless(makeImage(Color.RED, 63)),
                dct("not a jpeg".getBytes(), 100),
                lossless(makeImage(Color.BLUE, 64)))))));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType, ContentBlock::position)
            .containsExactly(tuple(BlockType.TEXT, 0), tuple(BlockType.IMAGE, 1));
        assertThat(colorOf(parsed.blocks().get(1).image())).isEqualTo(Color.BLUE.getRGB());
    }

    @Test
    void shouldRejectAPdfWithMoreThan100DistinctImages() throws IOException {
        List<ImageSpec> images = IntStream.range(0, 101)
            .mapToObj(i -> lossless(makeImage(new Color(i, 0, 255 - i), 64)))
            .toList();
        byte[] pdf = makePdfWithImages(List.of(new PageSpec("Atlas", images)));

        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> parser.parse(pdf))
            .withMessage("The document has 101 images (limit 100)");
    }

    @Test
    void shouldKeepPositionsConsecutiveAndTextFromTextBlocksOnly() throws IOException {
        ParsedDocument parsed = parser.parse(makePdfWithImages(List.of(
            new PageSpec("Intro", List.of(lossless(makeImage(Color.RED, 80)))),
            new PageSpec("", List.of(lossless(makeImage(Color.BLUE, 80)), lossless(makeImage(Color.GREEN, 80)))),
            new PageSpec("Summary", List.of()))));

        assertThat(parsed.blocks()).extracting(ContentBlock::blockType, ContentBlock::position)
            .containsExactly(
                tuple(BlockType.TEXT, 0), tuple(BlockType.IMAGE, 1), tuple(BlockType.IMAGE, 2),
                tuple(BlockType.IMAGE, 3), tuple(BlockType.TEXT, 4));
        assertThat(parsed.text()).isEqualTo("Intro\n\nSummary");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** An image to draw: a lossless image, or bytes stored as a {@code size}x{@code size} DCT (JPEG) stream. */
    private record ImageSpec(BufferedImage lossless, byte[] dct, int size) {
    }

    private record PageSpec(String text, List<ImageSpec> images) {
    }

    private static ImageSpec lossless(BufferedImage image) {
        return new ImageSpec(image, null, image.getWidth());
    }

    private static ImageSpec dct(byte[] bytes, int size) {
        return new ImageSpec(null, bytes, size);
    }

    private static BufferedImage makeImage(Color color, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, size, size);
        graphics.dispose();
        return image;
    }

    private static byte[] makeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static int colorOf(ImageInput image) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(image.data())).getRGB(0, 0) | 0xFF000000;
    }

    /** Each page's text (if any), then its images drawn in list order; one XObject per distinct spec. */
    private static byte[] makePdfWithImages(List<PageSpec> pages) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Map<ImageSpec, PDImageXObject> xObjects = new IdentityHashMap<>();
            for (PageSpec spec : pages) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                List<PDImageXObject> drawn = new ArrayList<>();
                for (ImageSpec image : spec.images()) {
                    PDImageXObject xObject = xObjects.get(image);
                    if (xObject == null) {
                        xObject = makeXObject(pdf, image);
                        xObjects.put(image, xObject);
                    }
                    drawn.add(xObject);
                }
                writePage(pdf, page, spec.text(), drawn);
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }

    /** One page whose images are added to the resources in list order but drawn in {@code drawOrder}. */
    private static byte[] makePdfWithImagesInDrawOrder(String text, List<ImageSpec> images, List<Integer> drawOrder)
        throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            pdf.addPage(page);
            List<PDImageXObject> xObjects = new ArrayList<>();
            for (ImageSpec image : images) {
                PDImageXObject xObject = makeXObject(pdf, image);
                page.getResources().add(xObject);
                xObjects.add(xObject);
            }
            writePage(pdf, page, text, drawOrder.stream().map(xObjects::get).toList());
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private static PDImageXObject makeXObject(PDDocument pdf, ImageSpec image) throws IOException {
        if (image.lossless() != null) {
            return LosslessFactory.createFromImage(pdf, image.lossless());
        }
        return new PDImageXObject(pdf, new ByteArrayInputStream(image.dct()), COSName.DCT_DECODE,
            image.size(), image.size(), 8, PDDeviceRGB.INSTANCE);
    }

    private static void writePage(PDDocument pdf, PDPage page, String text, List<PDImageXObject> images)
        throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
            if (!text.isEmpty()) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                stream.showText(text);
                stream.endText();
            }
            for (PDImageXObject image : images) {
                stream.drawImage(image, 72, 72, 64, 64);
            }
        }
    }

    private static byte[] makePdf(String title, String... pageTexts) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pdf.getDocumentInformation().setTitle(title);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                if (text.isEmpty()) {
                    continue;
                }
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }
}
