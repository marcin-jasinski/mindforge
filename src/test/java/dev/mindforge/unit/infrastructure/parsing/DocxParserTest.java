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

    private static void addStyle(XWPFStyles styles, String id, String name) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId(id);
        style.addNewName().setVal(name);
        styles.addStyle(new XWPFStyle(style));
    }
}
