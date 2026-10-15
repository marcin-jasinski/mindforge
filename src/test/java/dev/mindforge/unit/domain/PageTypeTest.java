package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.PageType;

class PageTypeTest {

    @Test
    void shouldNormaliseKnownTypesToTheirCanonicalSpelling() {
        assertThat(new PageType("  concept ")).isEqualTo(PageType.CONCEPT);
        assertThat(new PageType("source\n  SUMMARY")).isEqualTo(PageType.SOURCE_SUMMARY);
        assertThat(PageType.SOURCE_SUMMARY.value()).isEqualTo("Source Summary");
    }

    @Test
    void shouldKeepAnUnknownTypeReadFromABundle() {
        assertThat(new PageType(" Topic ").value()).isEqualTo("Topic");
    }

    @Test
    void shouldRejectABlankType() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageType(" \t"));
    }
}
