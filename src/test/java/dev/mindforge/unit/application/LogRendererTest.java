package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.wiki.LogRenderer;
import dev.mindforge.domain.model.LogEntry;
import dev.mindforge.domain.model.LogEntry.RevertedRun;
import dev.mindforge.domain.model.RunKind;

class LogRendererTest {

    private static final Instant EARLIER_DAY = Instant.parse("2026-09-09T12:00:00Z");

    private static final RevertedRun REVERTED_INGEST =
        new RevertedRun(RunKind.INGEST, EARLIER_DAY, false, "biologia-lekcja-2", "Biologia — lekcja 2");

    @Test
    void shouldRenderEveryLineShapeNewestFirstGroupedByUtcDate() {
        String log = LogRenderer.render(List.of(
            makeEntry(RunKind.INGEST, "2026-09-09T12:00:00Z", false, "biologia-lekcja-2", "Biologia — lekcja 2",
                4, 0, 0, 0, null),
            makeEntry(RunKind.REVERT, "2026-09-10T08:00:00Z", false, null, null, 0, 0, 0, 1, REVERTED_INGEST),
            makeEntry(RunKind.INGEST, "2026-09-10T23:59:00Z", false, "biologia-lekcja-3", "Biologia — lekcja 3",
                2, 5, 0, 1, null),
            makeEntry(RunKind.REVERT, "2026-09-10T09:00:00Z", false, null, null, 0, 12, 0, 0,
                new RevertedRun(RunKind.LINT, EARLIER_DAY, false, null, null)),
            makeEntry(RunKind.INGEST, "2026-09-10T13:00:00Z", true, "conversation", "Conversation", 0, 1, 1, 0, null),
            makeEntry(RunKind.REVERT, "2026-09-10T11:00:00Z", false, null, null, 0, 3, 2, 1, REVERTED_INGEST),
            makeEntry(RunKind.LINT, "2026-09-10T12:00:00Z", false, null, null, 0, 12, 0, 0, null),
            makeEntry(RunKind.REVERT, "2026-09-10T10:00:00Z", false, null, null, 0, 1, 0, 0,
                new RevertedRun(RunKind.INGEST, EARLIER_DAY, true, "conversation", "Conversation"))));

        assertThat(log).isEqualTo("""
            # Update Log

            ## 2026-09-10
            * **Ingest**: [Biologia — lekcja 3](/sources/biologia-lekcja-3.md) — 2 created, 5 revised, 1 claim superseded.
            * **Edit**: conversation — 1 revised, 1 deleted.
            * **Lint**: links added to 12 pages.
            * **Revert**: undid the ingest of 2026-09-09 ([Biologia — lekcja 2](/sources/biologia-lekcja-2.md)) — 3 pages restored, 2 pages removed, 1 supersession removed.
            * **Revert**: undid the edit of 2026-09-09 — 1 page restored.
            * **Revert**: undid the Lint of 2026-09-09 — 12 pages restored.
            * **Revert**: removed 1 supersession from the ingest of 2026-09-09 ([Biologia — lekcja 2](/sources/biologia-lekcja-2.md)).

            ## 2026-09-09
            * **Ingest**: [Biologia — lekcja 2](/sources/biologia-lekcja-2.md) — 4 created.
            """);
    }

    @Test
    void shouldOmitRunsThatChangedNothingAndDaysLeftEmpty() {
        String log = LogRenderer.render(List.of(
            makeEntry(RunKind.INGEST, "2026-09-10T08:00:00Z", false, "lekcja-1", "Lekcja [1]", 0, 0, 0, 0, null),
            makeEntry(RunKind.LINT, "2026-09-10T09:00:00Z", false, null, null, 0, 0, 0, 0, null),
            makeEntry(RunKind.INGEST, "2026-09-09T08:00:00Z", false, "lekcja-1", "Lekcja [1]", 1, 0, 0, 2, null)));

        assertThat(log).isEqualTo("""
            # Update Log

            ## 2026-09-09
            * **Ingest**: [Lekcja \\[1\\]](/sources/lekcja-1.md) — 1 created, 2 claims superseded.
            """);
    }

    @Test
    void shouldRenderOnlyTheHeadingWhenNothingIsLogged() {
        assertThat(LogRenderer.render(List.of())).isEqualTo("# Update Log\n");
    }

    private static LogEntry makeEntry(RunKind kind, String finishedAt, boolean conversation, String lessonId,
                                      String lessonTitle, int created, int revised, int deleted,
                                      int supersessionCount, RevertedRun reverted) {
        return new LogEntry(UUID.randomUUID(), kind, Instant.parse(finishedAt), conversation, lessonId, lessonTitle,
            created, revised, deleted, supersessionCount, reverted);
    }
}
