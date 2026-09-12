package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.Identifier;

class IdentifierTest {

    @Test
    void shouldTransliterateGreekLettersByTheirPolishNames() {
        assertThat(Identifier.slugify("α-helisa")).isEqualTo("alfa-helisa");
        assertThat(Identifier.slugify("β-helisa")).isEqualTo("beta-helisa");
        assertThat(Identifier.slugify("π")).isEqualTo("pi");
    }

    @Test
    void shouldTransliteratePolishWithoutAHashSuffix() {
        assertThat(Identifier.slugify("Źdźbło łąki, Ø ß")).isEqualTo("zdzblo-laki-o-ss");
    }

    @Test
    void shouldReplaceAnUntransliterableTitleWithAHash() {
        assertThat(Identifier.slugify("細胞")).matches("x-[0-9a-f]{8}");
    }

    @Test
    void shouldKeepTitlesDistinctWhenLettersAreDropped() {
        String a = Identifier.slugify("細胞 A");
        String b = Identifier.slugify("細胞 B");

        assertThat(a).matches("a-[0-9a-f]{8}");
        assertThat(b).matches("b-[0-9a-f]{8}");
        assertThat(a.substring(2)).isNotEqualTo(b.substring(2));
    }

    @Test
    void shouldCapAt80CharactersKeepingTheHashSuffix() {
        String slug = Identifier.slugify("a".repeat(100) + " 細");

        assertThat(slug).hasSize(80).matches("a{71}-[0-9a-f]{8}");
    }

    @Test
    void shouldNotEndInAHyphenWhenTheCapFallsOnASeparator() {
        String slug = Identifier.slugify("word ".repeat(30));

        assertThat(slug).hasSizeLessThanOrEqualTo(80).matches("[a-z0-9]+(-[a-z0-9]+)*");
    }

    @Test
    void shouldAcceptOnlyLowercaseHyphenSeparatedIdentifiersUpTo80Characters() {
        assertThat(Identifier.matches("alfa-helisa")).isTrue();
        assertThat(Identifier.matches("a".repeat(80))).isTrue();

        assertThat(Identifier.matches("a--b")).isFalse();
        assertThat(Identifier.matches("-x")).isFalse();
        assertThat(Identifier.matches("Mitoza")).isFalse();
        assertThat(Identifier.matches("bio_3")).isFalse();
        assertThat(Identifier.matches("")).isFalse();
        assertThat(Identifier.matches("a".repeat(81))).isFalse();
    }

    @Test
    void shouldBoundTheLengthInThePatternItselfSoValidatorsCanEmbedIt() {
        assertThat(Identifier.PATTERN.matcher("a".repeat(80)).matches()).isTrue();
        assertThat(Identifier.PATTERN.matcher("a".repeat(81)).matches()).isFalse();
        assertThat(("concepts/" + "a".repeat(81) + ".md")
            .matches("concepts/" + Identifier.PATTERN.pattern() + "\\.md")).isFalse();
        assertThat("sources/bio-3.md".matches("sources/" + Identifier.PATTERN.pattern() + "\\.md")).isTrue();
    }
}
