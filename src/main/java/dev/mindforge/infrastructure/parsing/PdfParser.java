package dev.mindforge.infrastructure.parsing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/** A PDF as one text block per non-blank page; the document's {@code Title} becomes metadata. */
public class PdfParser implements FormatParser {

    public static final String MIME_TYPE = "application/pdf";

    private static final String PAGE_SEPARATOR = "\n\n";

    @Override
    public ParsedDocument parse(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            List<ContentBlock> blocks = new ArrayList<>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).strip();
                if (!text.isEmpty()) {
                    blocks.add(ContentBlock.text(text, blocks.size()));
                }
            }
            String title = pdf.getDocumentInformation().getTitle();
            return new ParsedDocument(
                blocks.stream().map(ContentBlock::content).collect(Collectors.joining(PAGE_SEPARATOR)),
                blocks,
                title == null ? Map.of() : Map.of(LessonIdentity.KEY_PDF_TITLE, title));
        } catch (IOException e) {
            throw new UploadRejectedException("The document is not a readable PDF", e);
        }
    }
}
