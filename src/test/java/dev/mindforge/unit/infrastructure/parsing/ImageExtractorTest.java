package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.parsing.ImageExtractor;

class ImageExtractorTest {

    private final ImageExtractor extractor = new ImageExtractor();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger extractorLog = (Logger) LoggerFactory.getLogger(ImageExtractor.class);

    ImageExtractorTest() {
        logs.start();
        extractorLog.addAppender(logs);
    }

    @AfterEach
    void detachLog() {
        extractorLog.detachAppender(logs);
    }

    @Test
    void shouldHaveTheLimitsAndNoAnnotation() {
        assertThat(ImageExtractor.MIN_DIMENSION).isEqualTo(64);
        assertThat(ImageExtractor.MAX_IMAGE_BYTES).isEqualTo(5 * 1024 * 1024);
        assertThat(ImageExtractor.MAX_IMAGES_PER_DOCUMENT).isEqualTo(100);
        assertThat(ImageExtractor.class.getAnnotations()).isEmpty();
    }

    @Test
    void shouldSkipAnImageNarrowerOrShorterThan64Px() throws IOException {
        assertThat(extractor.accept(makeImageBytes(32, 32, "png", 1), "image/png")).isEmpty();
        assertThat(extractor.accept(makeImageBytes(63, 200, "png", 2), "image/png")).isEmpty();
        assertThat(extractor.accept(makeImageBytes(200, 63, "gif", 3), "image/gif")).isEmpty();
        assertThat(extractor.accept(makeImage(32, 200, 4))).isEmpty();
    }

    @Test
    void shouldAcceptA64By64Image() throws IOException {
        assertThat(extractor.accept(makeImageBytes(64, 64, "png", 1), "image/png")).isPresent();
        assertThat(extractor.accept(makeImage(64, 64, 2))).isPresent();
    }

    @Test
    void shouldKeepPngAndJpegBytesAsTheyAre() throws IOException {
        byte[] png = makeImageBytes(80, 80, "png", 1);
        byte[] jpeg = makeImageBytes(80, 80, "jpg", 2);

        assertThat(extractor.accept(png, "image/png")).contains(new ImageInput(png, "image/png"));
        assertThat(extractor.accept(jpeg, "image/jpeg")).contains(new ImageInput(jpeg, "image/jpeg"));
    }

    @Test
    void shouldTakeTheFormatFromTheBytesNotFromTheDeclaredMimeType() throws IOException {
        byte[] jpeg = makeImageBytes(80, 80, "jpg", 1);
        byte[] gif = makeImageBytes(80, 80, "gif", 2);

        assertThat(extractor.accept(jpeg, "image/png")).contains(new ImageInput(jpeg, "image/jpeg"));
        ImageInput reEncoded = extractor.accept(gif, "image/png").orElseThrow();
        assertThat(reEncoded.mimeType()).isEqualTo("image/png");
        assertThat(reEncoded.data()).isNotEqualTo(gif);
        assertThat(ImageIO.getImageReaders(ImageIO.createImageInputStream(new ByteArrayInputStream(reEncoded.data())))
            .next().getFormatName()).isEqualToIgnoringCase("png");
    }

    @Test
    void shouldSkipAnImageWithTooManyPixelsBeforeDecodingIt() throws IOException {
        byte[] bomb = makePngWithDeclaredSize(makeImageBytes(64, 64, "png", 1), 40_000, 40_000);

        assertThatCode(() -> assertThat(extractor.accept(bomb, "image/png")).isEmpty()).doesNotThrowAnyException();
        assertThat(logs.list)
            .filteredOn(e -> e.getLevel() == Level.WARN)
            .singleElement()
            .extracting(ILoggingEvent::getFormattedMessage)
            .asString()
            .contains("40000x40000", "limit " + ImageExtractor.MAX_PIXELS);
    }

    @Test
    void shouldReEncodeGifAndBmpToPng() throws IOException {
        Optional<ImageInput> gif = extractor.accept(makeImageBytes(80, 80, "gif", 1), "image/gif");
        Optional<ImageInput> bmp = extractor.accept(makeImageBytes(90, 70, "bmp", 2), "image/bmp");

        assertThat(gif).get().extracting(ImageInput::mimeType).isEqualTo("image/png");
        assertThat(bmp).get().extracting(ImageInput::mimeType).isEqualTo("image/png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bmp.orElseThrow().data()));
        assertThat(decoded.getWidth()).isEqualTo(90);
        assertThat(decoded.getHeight()).isEqualTo(70);
        assertThat(ImageIO.read(new ByteArrayInputStream(gif.orElseThrow().data()))).isNotNull();
    }

    @Test
    void shouldEmitADecodedImageAsPng() throws IOException {
        ImageInput image = extractor.accept(makeImage(70, 70, 1)).orElseThrow();

        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(ImageIO.read(new ByteArrayInputStream(image.data()))).isNotNull();
    }

    @Test
    void shouldSkipVectorFormatsAndUndecodableBytesWithoutAnException() throws IOException {
        byte[] png = makeImageBytes(80, 80, "png", 1);

        assertThat(extractor.accept(png, "image/x-emf")).isEmpty();
        assertThat(extractor.accept(png, "image/x-wmf")).isEmpty();
        assertThat(extractor.accept(png, "image/svg+xml")).isEmpty();
        assertThatCode(() -> {
            assertThat(extractor.accept("not an image".getBytes(), "image/png")).isEmpty();
            assertThat(extractor.accept("not an image".getBytes(), "image/gif")).isEmpty();
            assertThat(extractor.accept(new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 1}, "image/jpeg")).isEmpty();
        }).doesNotThrowAnyException();
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(3);
    }

    @Test
    void shouldSkipAnImageOver5MbWithAWarning() {
        assertThat(extractor.accept(makeNoiseImage(1400, 1400))).isEmpty();

        assertThat(logs.list)
            .filteredOn(e -> e.getLevel() == Level.WARN)
            .singleElement()
            .extracting(ILoggingEvent::getFormattedMessage)
            .asString()
            .contains("limit " + ImageExtractor.MAX_IMAGE_BYTES);
    }

    @Test
    void shouldAcceptTheSameImageOnceAndNotCountDuplicates() throws IOException {
        byte[] png = makeImageBytes(80, 80, "png", 1);

        assertThat(extractor.accept(png, "image/png")).isPresent();
        assertThat(extractor.accept(png, "image/png")).isEmpty();
        assertThat(extractor.accept(png, "image/png")).isEmpty();

        int accepted = 1;
        for (int i = 2; i <= ImageExtractor.MAX_IMAGES_PER_DOCUMENT; i++) {
            byte[] distinct = makeImageBytes(64, 64, "png", i);
            accepted += extractor.accept(distinct, "image/png").isPresent() ? 1 : 0;
            assertThat(extractor.accept(distinct, "image/png")).isEmpty();
        }
        int images = accepted;

        assertThat(images).isEqualTo(100);
        assertThatCode(() -> extractor.checkCount(images)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMoreThan100Images() {
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> extractor.checkCount(101))
            .withMessage("The document has 101 images (limit 100)");
        assertThatCode(() -> extractor.checkCount(100)).doesNotThrowAnyException();
    }

    @Test
    void shouldNeverLogImageBytes() throws IOException {
        byte[] small = makeImageBytes(32, 32, "png", 1);
        byte[] png = makeImageBytes(80, 80, "png", 2);
        byte[] garbage = "not an image".getBytes();
        extractor.accept(small, "image/png");
        extractor.accept(png, "image/png");
        extractor.accept(png, "image/png");
        extractor.accept(png, "image/svg+xml");
        extractor.accept(garbage, "image/gif");

        assertThat(logs.list).hasSize(4).allSatisfy(event -> assertThat(event.getFormattedMessage())
            .doesNotContain(Base64.getEncoder().encodeToString(small))
            .doesNotContain(Base64.getEncoder().encodeToString(png))
            .doesNotContain(new String(garbage))
            .hasSizeLessThan(200));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static BufferedImage makeImage(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(seed * 2_654_435 & 0xFFFFFF));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static byte[] makeImageBytes(int width, int height, String format, int seed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(makeImage(width, height, seed), format, out)).isTrue();
        return out.toByteArray();
    }

    /** Rewrites the IHDR width and height of a PNG (and its CRC), leaving the pixel data small. */
    private static byte[] makePngWithDeclaredSize(byte[] png, int width, int height) {
        byte[] out = png.clone();
        ByteBuffer buffer = ByteBuffer.wrap(out);
        buffer.putInt(16, width);
        buffer.putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(out, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        return out;
    }

    private static BufferedImage makeNoiseImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        return image;
    }
}
