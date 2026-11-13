package dev.mindforge.infrastructure.parsing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.UploadRejectedException;

/**
 * Decides, per image a parser extracts, whether it becomes an {@link ImageInput}: decodable raster only, PNG and JPEG
 * kept as they are, any other raster re-encoded to PNG; small, oversized and duplicate images skipped. One instance
 * per {@code parse} call: it remembers the images already emitted for the document.
 */
public final class ImageExtractor {

    public static final int MIN_DIMENSION = 64;
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_IMAGES_PER_DOCUMENT = 100;
    /** Images above this pixel count are skipped from the header, before the decode allocates the raster. */
    public static final long MAX_PIXELS = 50_000_000L;

    private static final Logger log = LoggerFactory.getLogger(ImageExtractor.class);

    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final Set<String> NON_RASTER = Set.of("image/x-emf", "image/x-wmf", "image/svg+xml");

    private final Set<String> emittedDigests = new HashSet<>();

    /**
     * An image as stored in the document, with its declared MIME type. The pass-through decision uses the format the
     * bytes actually have, and the header dimensions are checked against {@link #MAX_PIXELS} before the decode.
     */
    public Optional<ImageInput> accept(byte[] bytes, String mimeType) {
        if (NON_RASTER.contains(mimeType)) {
            log.info("Image skipped: {} is not a raster format", mimeType);
            return Optional.empty();
        }
        String format;
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                log.warn("Image skipped: {} of {} bytes does not decode (no reader)", mimeType, bytes.length);
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    log.warn("Image skipped: {} {}x{} has {} pixels (limit {})",
                        mimeType, reader.getWidth(0), reader.getHeight(0), pixels, MAX_PIXELS);
                    return Optional.empty();
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Image skipped: {} of {} bytes does not decode ({})", mimeType, bytes.length, e.toString());
            return Optional.empty();
        }
        if (image == null) {
            log.warn("Image skipped: {} of {} bytes does not decode (no image)", mimeType, bytes.length);
            return Optional.empty();
        }
        if ("png".equals(format)) {
            return emit(bytes, PNG, image.getWidth(), image.getHeight());
        }
        if ("jpeg".equals(format) || "jpg".equals(format)) {
            return emit(bytes, JPEG, image.getWidth(), image.getHeight());
        }
        return accept(image);
    }

    /** An image already decoded (for example a PDF image XObject); it is emitted as PNG. */
    public Optional<ImageInput> accept(BufferedImage image) {
        if (isTooSmall(image.getWidth(), image.getHeight())) {
            return Optional.empty();
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", png)) {
                log.warn("Image skipped: {}x{} image has no PNG encoding", image.getWidth(), image.getHeight());
                return Optional.empty();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Image skipped: {}x{} image does not encode to PNG ({})",
                image.getWidth(), image.getHeight(), e.toString());
            return Optional.empty();
        }
        return emit(png.toByteArray(), PNG, image.getWidth(), image.getHeight());
    }

    /** Rejects the upload when the document has more IMAGE blocks than the limit. */
    public void checkCount(int images) {
        if (images > MAX_IMAGES_PER_DOCUMENT) {
            throw new UploadRejectedException(
                "The document has " + images + " images (limit " + MAX_IMAGES_PER_DOCUMENT + ")");
        }
    }

    private Optional<ImageInput> emit(byte[] bytes, String mimeType, int width, int height) {
        if (isTooSmall(width, height)) {
            return Optional.empty();
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            log.warn("Image skipped: {} {}x{} is {} bytes (limit {})",
                mimeType, width, height, bytes.length, MAX_IMAGE_BYTES);
            return Optional.empty();
        }
        if (!emittedDigests.add(sha256Hex(bytes))) {
            log.info("Image skipped: {} {}x{} is a duplicate in the document", mimeType, width, height);
            return Optional.empty();
        }
        return Optional.of(new ImageInput(bytes, mimeType));
    }

    private static boolean isTooSmall(int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            log.info("Image skipped: {}x{} is smaller than {} px", width, height, MIN_DIMENSION);
            return true;
        }
        return false;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
