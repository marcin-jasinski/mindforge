package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.parsing.PdfParser;

class PdfParserTest {

    private final PdfParser parser = new PdfParser();

    @Test
    void shouldReadEachNonBlankPageAsATextBlockAndTheTitleAsMetadata() throws IOException {
        ParsedDocument parsed = parser.parse(makePdf("Biologia — lekcja 3", "Cell division", "", "Mitosis phases"));

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.text("Cell division", 0),
            ContentBlock.text("Mitosis phases", 1));
        assertThat(parsed.text()).isEqualTo("Cell division\n\nMitosis phases");
        assertThat(parsed.metadata()).isEqualTo(Map.of("Title", "Biologia — lekcja 3"));
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

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

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
