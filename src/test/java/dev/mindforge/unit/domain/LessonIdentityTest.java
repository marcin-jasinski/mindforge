package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;

class LessonIdentityTest {

    @Test
    void shouldResolveFromFrontmatterLessonIdFirst() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("lesson_id", "algebra-101", "title", "Linear Algebra", "Title", "PDF Title"),
            "whatever.md");

        assertThat(identity.lessonId()).isEqualTo("algebra-101");
        assertThat(identity.title()).isEqualTo("Linear Algebra");
    }

    @Test
    void shouldFallBackToFilenameStemForTitleWhenLessonIdHasNoTitle() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("lesson_id", "algebra-101"), "/uploads/Linear Algebra.md");

        assertThat(identity.lessonId()).isEqualTo("algebra-101");
        assertThat(identity.title()).isEqualTo("Linear Algebra");
    }

    @Test
    void shouldUseLessonIdAsTitleWhenNoOtherTitleSource() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("lesson_id", "algebra-101"), null);

        assertThat(identity.lessonId()).isEqualTo("algebra-101");
        assertThat(identity.title()).isEqualTo("algebra-101");
    }

    @Test
    void shouldResolveFromFrontmatterTitleWhenNoLessonId() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("title", "Intro to Graphs!"), "ignored.md");

        assertThat(identity.lessonId()).isEqualTo("intro-to-graphs");
        assertThat(identity.title()).isEqualTo("Intro to Graphs!");
    }

    @Test
    void shouldResolveFromPdfTitleWhenNoFrontmatter() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("Title", "Quantum Mechanics"), "scan.pdf");

        assertThat(identity.lessonId()).isEqualTo("quantum-mechanics");
        assertThat(identity.title()).isEqualTo("Quantum Mechanics");
    }

    @Test
    void shouldResolveFromFilenameStemWhenNoMetadata() {
        LessonIdentity identity = LessonIdentity.resolve(Map.of(), "/uploads/Chapter 7.pdf");

        assertThat(identity.lessonId()).isEqualTo("chapter-7");
        assertThat(identity.title()).isEqualTo("Chapter 7");
    }

    @Test
    void shouldRejectWhenNoMetadataAndNoFilename() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of(), null));
    }

    @Test
    void shouldRejectWhenFilenameHasNoUsableStem() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of(), "   "));
    }

    @Test
    void shouldRejectExplicitLessonIdWithIllegalCharacters() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "Has Spaces"), "x.md"))
            .withMessageContaining("illegal characters");
    }

    @Test
    void shouldRejectLessonIdExceedingMaxLength() {
        String tooLong = "a".repeat(81);
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", tooLong), "x.md"))
            .withMessageContaining("exceeds");
    }

    @Test
    void shouldRejectReservedNames() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "index"), "x.md"))
            .withMessageContaining("reserved");
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "default"), "x.md"));
    }

    @Test
    void shouldPreserveUnderscoresWhenSlugifying() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("title", "my_lesson name"), "x.md");

        assertThat(identity.lessonId()).isEqualTo("my_lesson-name");
    }
}
