package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.wiki.PageRenderer;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.SourceCitation;

class PageRendererTest {

    private static final UUID PAGE = UUID.randomUUID();

    @Test
    void shouldPutASupersessionNoteUnderItsHeadingAndNotUnderAFencedLookalike() {
        String body = "```\n# Faza\n```\n\n# Faza\n\nProfaza trwa godzinę.\n\n# Etapy\n\nProfaza.\n";

        String rendered = PageRenderer.withNotes(body, List.of(
            new LiveSupersession(UUID.randomUUID(), PAGE, "faza", UUID.randomUUID(), "concepts/mejoza", "Mejoza [nowa]")));

        assertThat(rendered).isEqualTo("```\n# Faza\n```\n\n# Faza\n\n> Superseded by [Mejoza \\[nowa\\]](/concepts/mejoza.md)."
            + "\n\nProfaza trwa godzinę.\n\n# Etapy\n\nProfaza.\n");
    }

    @Test
    void shouldCiteEachLessonOfAConceptOnceByItsNewestTitleAndEachConversationTurnByDate() {
        List<SourceCitation> sources = List.of(
            makeSource("bio-1", "Lekcja 1", "2026-09-01T10:00:00Z", false),
            makeSource("conversation", "Conversation", "2026-09-02T10:00:00Z", true),
            makeSource("bio-1", "Lekcja 1 — poprawiona", "2026-09-03T10:00:00Z", false));

        assertThat(PageRenderer.withCitations("Treść.\n", PageType.CONCEPT, sources)).isEqualTo("Treść.\n\n# Citations\n\n"
            + "[1] Conversation, 2026-09-02\n[2] [Lekcja 1 — poprawiona](/sources/bio-1.md)\n");
    }

    @Test
    void shouldCiteASourceSummarysDocumentsByFilenameAndUploadDate() {
        List<SourceCitation> sources = List.of(makeSource("bio-1", "Lekcja 1", "2026-09-10T10:00:00Z", false));

        assertThat(PageRenderer.withCitations("Treść.\n", PageType.SOURCE_SUMMARY, sources))
            .isEqualTo("Treść.\n\n# Citations\n\n[1] notatki.pdf, uploaded 2026-09-10\n");
    }

    @Test
    void shouldAddNoCitationsSectionWithoutSources() {
        assertThat(PageRenderer.withCitations("Treść.\n", PageType.CONCEPT, List.of())).isEqualTo("Treść.\n");
    }

    private static SourceCitation makeSource(String lessonId, String lessonTitle, String uploadedAt,
                                             boolean conversation) {
        return new SourceCitation(PAGE, UUID.randomUUID(), lessonId, lessonTitle, "notatki.pdf", conversation,
            Instant.parse(uploadedAt));
    }
}
