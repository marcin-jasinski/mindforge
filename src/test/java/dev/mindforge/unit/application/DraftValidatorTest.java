package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.DraftValidator;
import dev.mindforge.domain.model.PageDraft;

class DraftValidatorTest {

    private static final String TITLE = "Mitosis";

    @Test
    void shouldAcceptADraftWithSectionsAndValidLinks() {
        assertThat(problem("# Phase\n\nSee [meiosis](/concepts/meiosis.md#stages) and [source](https://example.org).\n",
            null, false)).isNull();
    }

    @Test
    void shouldRejectAnInvalidPageLink() {
        assertThat(problem("See [meiosis](meiosis.md).\n", null, false)).startsWith("invalid link");
    }

    @Test
    void shouldRejectAnImage() {
        assertThat(problem("![diagram](https://example.org/a.png)\n", null, false)).startsWith("invalid link");
    }

    @Test
    void shouldRejectAReferenceDefinition() {
        assertThat(problem("See [meiosis].\n\n[meiosis]: /concepts/meiosis.md\n", null, false))
            .startsWith("invalid link");
    }

    @Test
    void shouldRejectFrontmatter() {
        assertThat(problem("---\ntype: Concept\n---\n# Phase\n", null, false)).isEqualTo("frontmatter");
    }

    @Test
    void shouldRejectACitationsSection() {
        assertThat(problem("# Phase\n\nA.\n\n# Citations\n\n* bio-1\n", null, false))
            .isEqualTo("a # Citations section");
    }

    @Test
    void shouldRejectALevelOneHeadingRepeatingTheTitle() {
        assertThat(problem("# Mitosis\n\nCell division.\n", null, false))
            .isEqualTo("a level-1 heading repeating the title");
    }

    @Test
    void shouldRejectADocumentRevisionThatDropsASection() {
        assertThat(problem("# Phase\n\nA.\n", "# Phase\n\nA.\n\n# Stages\n\nB.\n", true))
            .isEqualTo("dropped sections: stages");
    }

    @Test
    void shouldLetAConversationEditDropASectionAndAHeadingChangeItsCase() {
        assertThat(problem("# Phase\n\nA.\n", "# Phase\n\nA.\n\n# Stages\n\nB.\n", false)).isNull();
        assertThat(problem("# PHASE!\n\nA.\n", "# Phase\n\nA.\n", true)).isNull();
    }

    @Test
    void shouldNotReadAHashCommentInsideAFenceAsAHeading() {
        String body = "# Phase\n\n```\n# Mitosis\n```\n";

        assertThat(problem(body, null, false)).isNull();
        assertThat(problem("```\n# Phase\n```\n", "# Phase\n\nA.\n", true)).isEqualTo("dropped sections: phase");
    }

    @Test
    void shouldRejectABlankBodyOrAnOverlongDescription() {
        assertThat(problem(" \r\n", null, false)).isEqualTo("empty body");
        assertThat(DraftValidator.problem(TITLE, DraftValidator.normalise(new PageDraft("x".repeat(301), "A.\n")),
            null, false)).contains("description must be 1-300 characters");
    }

    @Test
    void shouldNormaliseTheDescriptionToOneLineAndTheBodyToItsStoredForm() {
        assertThat(DraftValidator.normalise(new PageDraft(" Cell\n division. ", "# Phase  \r\nA.\n\n\n")))
            .isEqualTo(new PageDraft("Cell division.", "# Phase\nA.\n"));
    }

    private static String problem(String body, String existingBody, boolean keepSections) {
        return DraftValidator.problem(TITLE, DraftValidator.normalise(new PageDraft("Description.", body)), existingBody,
            keepSections).orElse(null);
    }
}
