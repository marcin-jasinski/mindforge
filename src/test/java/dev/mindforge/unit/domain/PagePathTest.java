package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.PagePath;

class PagePathTest {

    @Test
    void shouldDeriveAConceptPathFromItsTitle() {
        assertThat(PagePath.concept("Cell mitosis")).isEqualTo("concepts/cell-mitosis");
    }

    @Test
    void shouldSuffixADerivedConceptNameThatLandsOnAReservedWord() {
        assertThat(PagePath.concept("Index")).isEqualTo("concepts/index-concept");
        assertThat(PagePath.concept("LOG")).isEqualTo("concepts/log-concept");
    }

    @Test
    void shouldBuildASourceSummaryPathFromAnExplicitLessonIdWithoutRewritingIt() {
        assertThat(PagePath.sourceSummary("biology-lesson-3")).isEqualTo("sources/biology-lesson-3");

        assertThatIllegalArgumentException().isThrownBy(() -> PagePath.sourceSummary("bio_3"));
        assertThatIllegalArgumentException().isThrownBy(() -> PagePath.sourceSummary("index"));
    }

    @Test
    void shouldAcceptOnlyAKnownDirectoryAndANonReservedIdentifier() {
        assertThat(PagePath.isValid("concepts/mitosis")).isTrue();
        assertThat(PagePath.isValid("sources/bio-3")).isTrue();

        assertThat(PagePath.isValid("topics/mitosis")).isFalse();
        assertThat(PagePath.isValid("concepts/Mitosis")).isFalse();
        assertThat(PagePath.isValid("concepts/log")).isFalse();
        assertThat(PagePath.isValid("concepts/mitosis.md")).isFalse();
        assertThat(PagePath.isValid("/concepts/mitosis")).isFalse();
    }
}
