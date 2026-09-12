package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.parsing.DocxParser;

class DocxParserTest {

    private final DocxParser parser = new DocxParser();

    @Test
    void shouldReadHeadingsParagraphsAndTablesInDocumentOrder() throws IOException {
        ParsedDocument parsed = parser.parse(makeDocx());

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.heading("Mitoza", 1, 0),
            ContentBlock.text("Przed tabelą.", 1),
            ContentBlock.text("Faza | Opis\nProfaza | Kondensacja", 2),
            ContentBlock.heading("Fazy", 2, 3),
            ContentBlock.text("Po tabeli.", 4));
        assertThat(parsed.text())
            .isEqualTo("Mitoza\n\nPrzed tabelą.\n\nFaza | Opis\nProfaza | Kondensacja\n\nFazy\n\nPo tabeli.");
        assertThat(parsed.metadata()).isEmpty();
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
            docx.getLastParagraph().createRun().setText("Mitoza");
            docx.createParagraph().createRun().setText("Przed tabelą.");
            XWPFTable table = docx.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Faza");
            table.getRow(0).getCell(1).setText("Opis");
            table.getRow(1).getCell(0).setText("Profaza");
            table.getRow(1).getCell(1).setText("Kondensacja");
            docx.createParagraph();
            docx.createParagraph().setStyle("Nagwek2");
            docx.getLastParagraph().createRun().setText("Fazy");
            docx.createParagraph().createRun().setText("Po tabeli.");

            docx.write(out);
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
