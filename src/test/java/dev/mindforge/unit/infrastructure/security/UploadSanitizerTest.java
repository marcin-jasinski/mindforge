package dev.mindforge.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.security.UploadSanitizer;

class UploadSanitizerTest {

    private static final long MAX_BYTES = 1_000;
    private static final String MARKDOWN = "text/markdown";

    private final UploadSanitizer sanitizer = new UploadSanitizer(MAX_BYTES, Set.of(MARKDOWN, "application/pdf"));

    @Test
    void shouldAdmitAnAllowedUploadAndReturnItsFilename() {
        assertThat(sanitizer.admit("Mitoza komórkowa (v2).md", MARKDOWN, MAX_BYTES))
            .isEqualTo("Mitoza komórkowa (v2).md");
    }

    @Test
    void shouldRejectAMimeTypeOutsideTheAllowlist() {
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> sanitizer.admit("script.sh", "application/x-sh", 10))
            .withMessageContaining("application/x-sh");
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> sanitizer.admit("notes.md", null, 10));
    }

    @Test
    void shouldRejectAnUploadLargerThanTheLimit() {
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> sanitizer.admit("notes.md", MARKDOWN, MAX_BYTES + 1));
    }

    @Test
    void shouldRejectPathTraversal() {
        for (String filename : new String[] {"../etc/passwd", "..\\boot.ini", "dir/notes.md", "..", "notes\0.md"}) {
            assertThatExceptionOfType(UploadRejectedException.class)
                .as(filename)
                .isThrownBy(() -> sanitizer.admit(filename, MARKDOWN, 10));
        }
    }

    @Test
    void shouldRejectABlankFilename() {
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> sanitizer.admit("  ", MARKDOWN, 10));
        assertThatExceptionOfType(UploadRejectedException.class)
            .isThrownBy(() -> sanitizer.admit(null, MARKDOWN, 10));
    }

    @Test
    void shouldReplaceUnsafeCharactersAndComposeDecomposedLetters() {
        assertThat(sanitizer.admit("notes<script>|\"x\".md", MARKDOWN, 10)).isEqualTo("notes_script___x_.md");
        assertThat(sanitizer.admit("komórka\n.md", MARKDOWN, 10)).isEqualTo("komórka_.md");
    }

    @Test
    void shouldCapALongFilename() {
        assertThat(sanitizer.admit("a".repeat(300) + ".md", MARKDOWN, 10)).hasSize(255);
    }
}
