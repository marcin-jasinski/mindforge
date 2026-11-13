package dev.mindforge.infrastructure.parsing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/**
 * A PDF as one text block per non-blank page, each followed by the page's accepted images in draw order; the
 * document's {@code Title} becomes metadata.
 */
public class PdfParser implements FormatParser {

    public static final String MIME_TYPE = "application/pdf";

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    private static final String PAGE_SEPARATOR = "\n\n";
    private static final String JPEG_SUFFIX = "jpg";

    @Override
    public ParsedDocument parse(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            ImageExtractor extractor = new ImageExtractor();
            List<ContentBlock> blocks = new ArrayList<>();
            int images = 0;
            Set<COSBase> processed = new HashSet<>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).strip();
                if (!text.isEmpty()) {
                    blocks.add(ContentBlock.text(text, blocks.size()));
                }
                for (PDImageXObject drawn : drawnImages(pdf.getPage(page - 1))) {
                    if (!processed.add(drawn.getCOSObject())) {
                        continue;
                    }
                    Optional<ImageInput> image = extract(drawn, extractor);
                    if (image.isPresent()) {
                        blocks.add(ContentBlock.image(image.get(), blocks.size()));
                        images++;
                        extractor.checkCount(images);
                    }
                }
            }
            String title = pdf.getDocumentInformation().getTitle();
            return new ParsedDocument(
                blocks.stream()
                    .filter(block -> block.blockType() != BlockType.IMAGE)
                    .map(ContentBlock::content)
                    .collect(Collectors.joining(PAGE_SEPARATOR)),
                blocks,
                title == null ? Map.of() : Map.of(LessonIdentity.KEY_PDF_TITLE, title));
        } catch (IOException e) {
            throw new UploadRejectedException("The document is not a readable PDF", e);
        }
    }

    private static List<PDImageXObject> drawnImages(PDPage page) throws IOException {
        DrawnImageCollector collector = new DrawnImageCollector();
        collector.processPage(page);
        return collector.images;
    }

    /** DCT streams keep their JPEG bytes; any other image is decoded and re-encoded as PNG. */
    private static Optional<ImageInput> extract(PDImageXObject image, ImageExtractor extractor) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < ImageExtractor.MIN_DIMENSION || height < ImageExtractor.MIN_DIMENSION) {
            log.info("Image skipped: {}x{} is smaller than {} px", width, height, ImageExtractor.MIN_DIMENSION);
            return Optional.empty();
        }
        if ((long) width * height > ImageExtractor.MAX_PIXELS) {
            log.warn("Image skipped: {}x{} has more than {} pixels", width, height, ImageExtractor.MAX_PIXELS);
            return Optional.empty();
        }
        try {
            if (JPEG_SUFFIX.equals(image.getSuffix())) {
                try (InputStream jpeg = image.getStream().createInputStream(List.of(COSName.DCT_DECODE.getName()))) {
                    return extractor.accept(jpeg.readAllBytes(), "image/jpeg");
                }
            }
            BufferedImage decoded = image.getImage();
            if (decoded == null) {
                log.warn("Image skipped: {}x{} PDF image does not decode (no image)", width, height);
                return Optional.empty();
            }
            return extractor.accept(decoded);
        } catch (IOException | RuntimeException e) {
            log.warn("Image skipped: {}x{} PDF image does not decode ({})", width, height, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Collects the distinct image XObjects a page draws, in order of first draw, including those inside form
     * XObjects; a repeated draw of the same object is not collected again.
     */
    private static final class DrawnImageCollector extends PDFStreamEngine {

        private final List<PDImageXObject> images = new ArrayList<>();
        private final Set<COSBase> formsInProgress = new HashSet<>();
        private final Set<COSBase> imagesSeen = new HashSet<>();

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (!OperatorName.DRAW_OBJECT.equals(operator.getName())
                || operands.isEmpty()
                || !(operands.get(0) instanceof COSName name)) {
                super.processOperator(operator, operands);
                return;
            }
            PDXObject xObject;
            try {
                PDResources resources = getResources();
                xObject = resources == null ? null : resources.getXObject(name);
            } catch (IOException | RuntimeException e) {
                log.warn("Image skipped: XObject {} does not load ({})", name.getName(), e.toString());
                return;
            }
            if (xObject instanceof PDImageXObject image) {
                if (imagesSeen.add(image.getCOSObject())) {
                    images.add(image);
                }
            } else if (xObject instanceof PDFormXObject form && formsInProgress.add(form.getCOSObject())) {
                try {
                    showForm(form);
                } finally {
                    formsInProgress.remove(form.getCOSObject());
                }
            }
        }
    }
}
