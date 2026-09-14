package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.TextRules;

class TextRulesTest {

    @Test
    void shouldNormaliseLineEndingsTrailingWhitespaceAndTheFinalNewline() {
        assertThat(TextRules.normaliseBody("# Mitoza  \r\n\r\nFaza\t\r\n\n\n"))
            .isEqualTo("# Mitoza\n\nFaza\n");
    }

    @Test
    void shouldAddTheFinalNewlineWhenItIsMissing() {
        assertThat(TextRules.normaliseBody("Faza")).isEqualTo("Faza\n");
    }

    @Test
    void shouldKeepLeadingIndentation() {
        assertThat(TextRules.normaliseBody("```\n    kod\n```")).isEqualTo("```\n    kod\n```\n");
    }
}
