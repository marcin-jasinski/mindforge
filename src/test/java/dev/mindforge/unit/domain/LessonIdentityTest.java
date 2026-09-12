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
    void shouldTransliteratePolishWhenDerivingTheLessonId() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("title", "Mitoza komórkowa"), "ignored.md");

        assertThat(identity.lessonId()).isEqualTo("mitoza-komorkowa");
        assertThat(identity.title()).isEqualTo("Mitoza komórkowa");
    }

    @Test
    void shouldResolveFromPdfTitleWhenNoFrontmatter() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("Title", "Quantum Mechanics"), "scan.pdf");

        assertThat(identity.lessonId()).isEqualTo("quantum-mechanics");
        assertThat(identity.title()).isEqualTo("Quantum Mechanics");
    }

    @Test
    void shouldFlattenAMultiLineTitleAndDropInvisibleCharacters() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("Title", "Mitoza\r\n\tkomórkowa\u200B "), "scan.pdf");

        assertThat(identity.title()).isEqualTo("Mitoza komórkowa");
        assertThat(identity.lessonId()).isEqualTo("mitoza-komorkowa");
    }

    @Test
    void shouldFallBackToFilenameStemWhenATitleIsBlankOnceCleaned() {
        LessonIdentity identity = LessonIdentity.resolve(Map.of("Title", "\u200B\n "), "Notatki.pdf");

        assertThat(identity.title()).isEqualTo("Notatki");
        assertThat(identity.lessonId()).isEqualTo("notatki");
    }

    @Test
    void shouldCutALongTitleTo200CharactersWithAnEllipsis() {
        LessonIdentity identity = LessonIdentity.resolve(Map.of("title", "a".repeat(250)), "x.md");

        assertThat(identity.title()).hasSize(200).isEqualTo("a".repeat(199) + "…");
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
            .withMessageContaining("must be 1-80 characters");
    }

    @Test
    void shouldRejectExplicitLessonIdOutsideTheIdentifierGrammarInsteadOfRewritingIt() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "bio_3"), "x.md"));
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "a--b"), "x.md"));
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", " bio-3 "), "x.md"));
    }

    @Test
    void shouldRejectLessonIdExceedingMaxLength() {
        String tooLong = "a".repeat(81);
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", tooLong), "x.md"))
            .withMessageContaining("must be 1-80 characters");
    }

    @Test
    void shouldRejectReservedNames() {
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "index"), "x.md"))
            .withMessageContaining("reserved");
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "default"), "x.md"));
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "log"), "x.md"));
        assertThatExceptionOfType(LessonIdentityException.class)
            .isThrownBy(() -> LessonIdentity.resolve(Map.of("lesson_id", "conversation"), "x.md"));
    }

    @Test
    void shouldSuffixADerivedLessonIdThatLandsOnAReservedWord() {
        assertThat(LessonIdentity.resolve(Map.of(), "index.md").lessonId()).isEqualTo("index-lesson");
        assertThat(LessonIdentity.resolve(Map.of("title", "Conversation"), "x.md").lessonId())
            .isEqualTo("conversation-lesson");
    }

    @Test
    void shouldTreatUnderscoresAsSeparatorsWhenSlugifying() {
        LessonIdentity identity = LessonIdentity.resolve(
            Map.of("title", "my_lesson name"), "x.md");

        assertThat(identity.lessonId()).isEqualTo("my-lesson-name");
    }
}
