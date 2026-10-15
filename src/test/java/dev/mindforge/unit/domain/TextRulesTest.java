package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.TextRules;

class TextRulesTest {

    @Test
    void shouldNormaliseLineEndingsTrailingWhitespaceAndTheFinalNewline() {
        assertThat(TextRules.normaliseBody("# Mitosis  \r\n\r\nPhase\t\r\n\n\n"))
            .isEqualTo("# Mitosis\n\nPhase\n");
    }

    @Test
    void shouldAddTheFinalNewlineWhenItIsMissing() {
        assertThat(TextRules.normaliseBody("Phase")).isEqualTo("Phase\n");
    }

    @Test
    void shouldKeepLeadingIndentation() {
        assertThat(TextRules.normaliseBody("```\n    code\n```")).isEqualTo("```\n    code\n```\n");
    }
}
