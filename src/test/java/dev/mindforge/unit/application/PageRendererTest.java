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
        String body = "```\n# Phase\n```\n\n# Phase\n\nProphase takes an hour.\n\n# Stages\n\nProphase.\n";

        String rendered = PageRenderer.withNotes(body, List.of(
            new LiveSupersession(UUID.randomUUID(), PAGE, "phase", UUID.randomUUID(), "concepts/meiosis", "Meiosis [new]")));

        assertThat(rendered).isEqualTo("```\n# Phase\n```\n\n# Phase\n\n> Superseded by [Meiosis \\[new\\]](/concepts/meiosis.md)."
            + "\n\nProphase takes an hour.\n\n# Stages\n\nProphase.\n");
    }

    @Test
    void shouldCiteEachLessonOfAConceptOnceByItsNewestTitleAndEachConversationTurnByDate() {
        List<SourceCitation> sources = List.of(
            makeSource("bio-1", "Lesson 1", "2026-09-01T10:00:00Z", false),
            makeSource("conversation", "Conversation", "2026-09-02T10:00:00Z", true),
            makeSource("bio-1", "Lesson 1 — corrected", "2026-09-03T10:00:00Z", false));

        assertThat(PageRenderer.withCitations("Content.\n", PageType.CONCEPT, sources)).isEqualTo("Content.\n\n# Citations\n\n"
            + "[1] Conversation, 2026-09-02\n[2] [Lesson 1 — corrected](/sources/bio-1.md)\n");
    }

    @Test
    void shouldCiteASourceSummarysDocumentsByFilenameAndUploadDate() {
        List<SourceCitation> sources = List.of(makeSource("bio-1", "Lesson 1", "2026-09-10T10:00:00Z", false));

        assertThat(PageRenderer.withCitations("Content.\n", PageType.SOURCE_SUMMARY, sources))
            .isEqualTo("Content.\n\n# Citations\n\n[1] notes.pdf, uploaded 2026-09-10\n");
    }

    @Test
    void shouldAddNoCitationsSectionWithoutSources() {
        assertThat(PageRenderer.withCitations("Content.\n", PageType.CONCEPT, List.of())).isEqualTo("Content.\n");
    }

    private static SourceCitation makeSource(String lessonId, String lessonTitle, String uploadedAt,
                                             boolean conversation) {
        return new SourceCitation(PAGE, UUID.randomUUID(), lessonId, lessonTitle, "notes.pdf", conversation,
            Instant.parse(uploadedAt));
    }
}
