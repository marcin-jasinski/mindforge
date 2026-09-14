package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.RevertService;
import dev.mindforge.application.wiki.LogRenderer;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RevertNotAllowedException;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class RevertAndLeaseTest extends TestContainerBase {

    private static final List<String> TENANT_TABLES = List.of("documents", "ingest_runs", "wiki_pages", "page_links",
        "page_revisions", "page_sources", "page_supersessions");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private RunReportQuery runReports;

    @Autowired
    private RevertService revertService;

    @Autowired
    private TransactionOperations transactions;

    // ---------------------------------------------------------------------------
    // Revert
    // ---------------------------------------------------------------------------

    @Test
    void revertOfADeletionReinsertsThePageUnderItsIdWithItsSourcesIntact() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson = insertDocument(kbId, userId, "bio-1", "API");
        UUID turn = insertDocument(kbId, userId, "conversation", "CONVERSATION");
        UUID pageId = UUID.randomUUID();
        completedRun(kbId, lesson, 0, runId -> {
            wiki.savePage(kbId, makeWrite(pageId, "concepts/mitoza", "# Faza\n"), runId);
            wiki.addSources(kbId, List.of(new PageSource(pageId, lesson, runId)));
        });
        UUID edit = completedRun(kbId, turn, 0, runId -> wiki.deletePage(kbId, pageId, runId));
        assertThat(wiki.findById(kbId, pageId)).isEmpty();

        UUID revert = revertService.revert(kbId, edit);

        assertThat(wiki.findById(kbId, pageId)).get()
            .extracting(WikiPage::path, WikiPage::markdownBody, WikiPage::revision)
            .containsExactly("concepts/mitoza", "# Faza\n", 3);
        assertThat(wiki.pageIdsForLesson(kbId, "bio-1")).containsExactly(pageId);
        assertThat(wiki.revisionsByRun(kbId, revert)).extracting(PageRevision::revision).containsExactly(3);
        assertThat(LogRenderer.render(runReports.logEntries(kbId)))
            .contains("* **Revert**: undid the edit of " + dayOf(kbId, edit) + " — 1 page restored.")
            .contains("* **Edit**: conversation — 1 deleted.");
    }

    @Test
    void revertRemovesProvenanceOnlyForThePagesItRestores() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson1 = insertDocument(kbId, userId, "bio-1", "API");
        UUID lesson2 = insertDocument(kbId, userId, "bio-2", "API");
        UUID created = UUID.randomUUID();
        UUID revisedLater = UUID.randomUUID();
        UUID ingest = completedRun(kbId, lesson1, 2, runId -> {
            wiki.savePage(kbId, makeWrite(created, "concepts/mitoza", "# Faza\n"), runId);
            wiki.savePage(kbId, makeWrite(revisedLater, "concepts/mejoza", "# Faza\n"), runId);
            wiki.addSources(kbId, List.of(new PageSource(created, lesson1, runId),
                new PageSource(revisedLater, lesson1, runId)));
            wiki.addSupersessions(kbId, List.of(
                new PageSupersession(UUID.randomUUID(), revisedLater, "faza", created, runId, Instant.now()),
                new PageSupersession(UUID.randomUUID(), created, "faza", revisedLater, runId, Instant.now())));
        });
        completedRun(kbId, lesson2, 0, runId -> {
            wiki.savePage(kbId, makeWrite(revisedLater, "concepts/mejoza", "# Faza\nNowsze.\n"), runId);
            wiki.addSources(kbId, List.of(new PageSource(revisedLater, lesson2, runId)));
        });

        revertService.revert(kbId, ingest);

        assertThat(wiki.findById(kbId, created)).isEmpty();
        assertThat(wiki.findById(kbId, revisedLater)).map(WikiPage::markdownBody).contains("# Faza\nNowsze.\n");
        assertThat(wiki.pageIdsForLesson(kbId, "bio-1")).containsExactly(revisedLater);
        String day = dayOf(kbId, ingest);
        assertThat(LogRenderer.render(runReports.logEntries(kbId)))
            .contains("* **Revert**: undid the ingest of " + day
                + " ([Lekcja bio-1](/sources/bio-1.md)) — 1 page removed, 1 supersession removed.")
            .contains("* **Ingest**: [Lekcja bio-2](/sources/bio-2.md) — 1 revised.")
            .contains("* **Ingest**: [Lekcja bio-1](/sources/bio-1.md) — 2 created, 2 claims superseded.");
    }

    @Test
    void aRevertRunIsNotRevertibleAndTheRefusalLeavesNoRun() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson = insertDocument(kbId, userId, "bio-1", "API");
        UUID ingest = completedRun(kbId, lesson, 0, runId ->
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/mitoza", "Treść.\n"), runId));
        UUID revert = revertService.revert(kbId, ingest);

        assertThatExceptionOfType(RevertNotAllowedException.class)
            .isThrownBy(() -> revertService.revert(kbId, revert));
        assertThatExceptionOfType(RevertNotAllowedException.class)
            .isThrownBy(() -> revertService.revert(kbId, ingest));
        assertThat(runs.hasQueuedOrActive(kbId, RunKind.REVERT)).isFalse();
    }

    @Test
    void revertIsRefusedWhileAnotherRunHoldsTheLease() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson = insertDocument(kbId, userId, "bio-1", "API");
        UUID pageId = UUID.randomUUID();
        UUID ingest = completedRun(kbId, lesson, 0, runId ->
            wiki.savePage(kbId, makeWrite(pageId, "concepts/mitoza", "Treść.\n"), runId));
        IngestRun active = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, lesson, null));
        assertThat(runs.claim(kbId, active.runId())).isTrue();

        assertThatExceptionOfType(KnowledgeBaseBusyException.class)
            .isThrownBy(() -> revertService.revert(kbId, ingest));

        assertThat(wiki.findById(kbId, pageId)).isPresent();
        assertThat(runs.hasQueuedOrActive(kbId, RunKind.REVERT)).isFalse();
    }

    // ---------------------------------------------------------------------------
    // Lease
    // ---------------------------------------------------------------------------

    @Test
    void ofTwoConcurrentClaimsExactlyOneWinsAndTheLoserStaysQueued() throws Exception {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson = insertDocument(kbId, userId, "bio-1", "API");
        IngestRun first = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, lesson, null));
        IngestRun second = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.LINT, null, null));

        CountDownLatch start = new CountDownLatch(1);
        boolean firstWon;
        boolean secondWon;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> firstClaim = executor.submit(claimWhenStarted(start, kbId, first.runId()));
            Future<Boolean> secondClaim = executor.submit(claimWhenStarted(start, kbId, second.runId()));
            start.countDown();
            firstWon = firstClaim.get();
            secondWon = secondClaim.get();
        }

        assertThat(List.of(firstWon, secondWon)).containsExactlyInAnyOrder(true, false);
        IngestRun winner = firstWon ? first : second;
        IngestRun loser = firstWon ? second : first;
        assertThat(runs.findById(kbId, winner.runId())).map(IngestRun::status).contains(RunStatus.RUNNING);
        assertThat(runs.findById(kbId, loser.runId())).map(IngestRun::status).contains(RunStatus.QUEUED);
        assertThat(runs.oldestQueued(kbId)).map(IngestRun::runId).contains(loser.runId());
    }

    @Test
    void aRunThatLostItsLeaseCannotCommit() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson = insertDocument(kbId, userId, "bio-1", "API");
        IngestRun run = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, lesson, null));
        IngestRun queued = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, lesson, null));
        assertThat(runs.claim(kbId, run.runId())).isTrue();
        // What the sweep does to a run it believes was interrupted.
        jdbc.update("UPDATE knowledge_bases SET active_run_id = NULL WHERE kb_id = ?", kbId);

        assertThatExceptionOfType(RunFencedException.class).isThrownBy(() -> transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, run.runId(), List.of());
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/mitoza", "Treść.\n"), run.runId());
        }));
        assertThatExceptionOfType(RunFencedException.class)
            .isThrownBy(() -> runs.markWritten(kbId, queued.runId(), List.of()));

        assertThat(wiki.findByPath(kbId, "concepts/mitoza")).isEmpty();
        assertThat(runs.findById(kbId, run.runId())).map(IngestRun::status).contains(RunStatus.RUNNING);
    }

    // ---------------------------------------------------------------------------
    // Deletion
    // ---------------------------------------------------------------------------

    @Test
    void deletingAKnowledgeBaseLeavesNoRowBehind() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        populate(kbId, userId);

        jdbc.update("DELETE FROM knowledge_bases WHERE kb_id = ?", kbId);

        assertNothingLeft(kbId);
    }

    @Test
    void deletingTheOwnerOfABusyKnowledgeBaseLeavesNoRowBehind() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson = populate(kbId, userId);
        IngestRun active = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, lesson, null));
        assertThat(runs.claim(kbId, active.runId())).isTrue();

        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);

        assertNothingLeft(kbId);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Every kind of wiki and history row, a supersession removal and a queued run; returns the lesson's document. */
    private UUID populate(UUID kbId, UUID userId) {
        UUID lesson = insertDocument(kbId, userId, "bio-1", "API");
        UUID p = UUID.randomUUID();
        UUID q = UUID.randomUUID();
        UUID removedSupersession = UUID.randomUUID();
        completedRun(kbId, lesson, 2, runId -> {
            wiki.savePage(kbId, makeWrite(p, "concepts/p", "# Faza\n[Q](/concepts/q.md)\n"), runId);
            wiki.savePage(kbId, makeWrite(q, "concepts/q", "# Faza\n"), runId);
            wiki.addSources(kbId, List.of(new PageSource(p, lesson, runId), new PageSource(q, lesson, runId)));
            wiki.addSupersessions(kbId, List.of(
                new PageSupersession(UUID.randomUUID(), q, "faza", p, runId, Instant.now()),
                new PageSupersession(removedSupersession, p, "faza", q, runId, Instant.now())));
        });
        revertService.removeSupersession(kbId, removedSupersession);
        runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.LINT, null, null));
        return lesson;
    }

    private void assertNothingLeft(UUID kbId) {
        for (String table : TENANT_TABLES) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE knowledge_base_id = ?",
                Integer.class, kbId)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_bases WHERE kb_id = ?", Integer.class, kbId))
            .isZero();
    }

    /** A completed ingest run whose writes commit under its lease, the way the pipeline's commits do. */
    private UUID completedRun(UUID kbId, UUID documentId, int supersessionCount, Consumer<UUID> writes) {
        UUID runId = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null)).runId();
        assertThat(runs.claim(kbId, runId)).isTrue();
        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, runId, List.of());
            writes.accept(runId);
        });
        runs.complete(kbId, runId, supersessionCount, false, List.of());
        return runId;
    }

    private Callable<Boolean> claimWhenStarted(CountDownLatch start, UUID kbId, UUID runId) {
        return () -> {
            start.await();
            return runs.claim(kbId, runId);
        };
    }

    private String dayOf(UUID kbId, UUID runId) {
        Instant finishedAt = runs.findById(kbId, runId).orElseThrow().finishedAt();
        return LocalDate.ofInstant(finishedAt, ZoneOffset.UTC).toString();
    }

    private static PageWrite makeWrite(UUID pageId, String path, String body) {
        return new PageWrite(pageId, path, "Tytuł " + path, "Opis.", PageType.CONCEPT, body);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "revert-" + userId + "@example.com");
        return userId;
    }

    private UUID insertKnowledgeBase(UUID ownerId) {
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, ownerId);
        return kbId;
    }

    private UUID insertDocument(UUID kbId, UUID userId, String lessonId, String uploadSource) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
                + " source_filename, mime_type, upload_source, uploaded_by)"
                + " VALUES (?, ?, ?, ?, ?, 'notatki.md', 'text/markdown', ?, ?)",
            documentId, kbId, lessonId, "Lekcja " + lessonId, documentId.toString(), uploadSource, userId);
        return documentId;
    }
}
