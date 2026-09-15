package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.DraftValidator;
import dev.mindforge.domain.model.PageDraft;

class DraftValidatorTest {

    private static final String TITLE = "Mitoza";

    @Test
    void shouldAcceptADraftWithSectionsAndValidLinks() {
        assertThat(problem("# Faza\n\nPatrz [mejoza](/concepts/mejoza.md#etapy) i [źródło](https://example.org).\n",
            null, false)).isNull();
    }

    @Test
    void shouldRejectAnInvalidPageLink() {
        assertThat(problem("Patrz [mejoza](mejoza.md).\n", null, false)).startsWith("invalid link");
    }

    @Test
    void shouldRejectAnImage() {
        assertThat(problem("![schemat](https://example.org/a.png)\n", null, false)).startsWith("invalid link");
    }

    @Test
    void shouldRejectAReferenceDefinition() {
        assertThat(problem("Patrz [mejoza].\n\n[mejoza]: /concepts/mejoza.md\n", null, false))
            .startsWith("invalid link");
    }

    @Test
    void shouldRejectFrontmatter() {
        assertThat(problem("---\ntype: Concept\n---\n# Faza\n", null, false)).isEqualTo("frontmatter");
    }

    @Test
    void shouldRejectACitationsSection() {
        assertThat(problem("# Faza\n\nA.\n\n# Citations\n\n* bio-1\n", null, false))
            .isEqualTo("a # Citations section");
    }

    @Test
    void shouldRejectALevelOneHeadingRepeatingTheTitle() {
        assertThat(problem("# Mitoza\n\nPodział komórki.\n", null, false))
            .isEqualTo("a level-1 heading repeating the title");
    }

    @Test
    void shouldRejectADocumentRevisionThatDropsASection() {
        assertThat(problem("# Faza\n\nA.\n", "# Faza\n\nA.\n\n# Etapy\n\nB.\n", true))
            .isEqualTo("dropped sections: etapy");
    }

    @Test
    void shouldLetAConversationEditDropASectionAndAHeadingChangeItsCase() {
        assertThat(problem("# Faza\n\nA.\n", "# Faza\n\nA.\n\n# Etapy\n\nB.\n", false)).isNull();
        assertThat(problem("# FAZA!\n\nA.\n", "# Faza\n\nA.\n", true)).isNull();
    }

    @Test
    void shouldNotReadAHashCommentInsideAFenceAsAHeading() {
        String body = "# Faza\n\n```\n# Mitoza\n```\n";

        assertThat(problem(body, null, false)).isNull();
        assertThat(problem("```\n# Faza\n```\n", "# Faza\n\nA.\n", true)).isEqualTo("dropped sections: faza");
    }

    @Test
    void shouldRejectABlankBodyOrAnOverlongDescription() {
        assertThat(problem(" \r\n", null, false)).isEqualTo("empty body");
        assertThat(DraftValidator.problem(TITLE, DraftValidator.normalise(new PageDraft("x".repeat(301), "A.\n")),
            null, false)).contains("description must be 1-300 characters");
    }

    @Test
    void shouldNormaliseTheDescriptionToOneLineAndTheBodyToItsStoredForm() {
        assertThat(DraftValidator.normalise(new PageDraft(" Podział\n komórki. ", "# Faza  \r\nA.\n\n\n")))
            .isEqualTo(new PageDraft("Podział komórki.", "# Faza\nA.\n"));
    }

    private static String problem(String body, String existingBody, boolean keepSections) {
        return DraftValidator.problem(TITLE, DraftValidator.normalise(new PageDraft("Opis.", body)), existingBody,
            keepSections).orElse(null);
    }
}
