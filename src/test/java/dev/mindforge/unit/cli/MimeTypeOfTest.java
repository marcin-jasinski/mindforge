package dev.mindforge.unit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.mindforge.cli.PipelineCommandRunner;

class MimeTypeOfTest {

    @ParameterizedTest
    @CsvSource({
        "notes.md, text/markdown",
        "NOTES.MD, text/markdown",
        "a.markdown, text/markdown",
        "a.txt, text/plain",
        "a.pdf, application/pdf",
        "a.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "dir/a.txt, text/plain",
        "a.sh, application/octet-stream",
        "README, application/octet-stream",
        "archive.tar.gz, application/octet-stream"
    })
    void theExtensionNamesTheMimeType(String file, String mimeType) {
        assertThat(PipelineCommandRunner.mimeTypeOf(Path.of(file))).isEqualTo(mimeType);
    }
}
