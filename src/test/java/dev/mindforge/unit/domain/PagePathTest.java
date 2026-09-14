package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.PagePath;

class PagePathTest {

    @Test
    void shouldDeriveAConceptPathFromItsTitle() {
        assertThat(PagePath.concept("Mitoza komórkowa")).isEqualTo("concepts/mitoza-komorkowa");
    }

    @Test
    void shouldSuffixADerivedConceptNameThatLandsOnAReservedWord() {
        assertThat(PagePath.concept("Index")).isEqualTo("concepts/index-concept");
        assertThat(PagePath.concept("LOG")).isEqualTo("concepts/log-concept");
    }

    @Test
    void shouldBuildASourceSummaryPathFromAnExplicitLessonIdWithoutRewritingIt() {
        assertThat(PagePath.sourceSummary("biologia-lekcja-3")).isEqualTo("sources/biologia-lekcja-3");

        assertThatIllegalArgumentException().isThrownBy(() -> PagePath.sourceSummary("bio_3"));
        assertThatIllegalArgumentException().isThrownBy(() -> PagePath.sourceSummary("index"));
    }

    @Test
    void shouldAcceptOnlyAKnownDirectoryAndANonReservedIdentifier() {
        assertThat(PagePath.isValid("concepts/mitoza")).isTrue();
        assertThat(PagePath.isValid("sources/bio-3")).isTrue();

        assertThat(PagePath.isValid("topics/mitoza")).isFalse();
        assertThat(PagePath.isValid("concepts/Mitoza")).isFalse();
        assertThat(PagePath.isValid("concepts/log")).isFalse();
        assertThat(PagePath.isValid("concepts/mitoza.md")).isFalse();
        assertThat(PagePath.isValid("/concepts/mitoza")).isFalse();
    }
}
