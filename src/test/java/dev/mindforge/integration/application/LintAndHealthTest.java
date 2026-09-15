package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.HealthService;
import dev.mindforge.application.service.LintService;
import dev.mindforge.application.service.RevertService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.KnowledgeBaseHealth;
import dev.mindforge.domain.model.LintAlreadyQueuedException;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class LintAndHealthTest extends TestContainerBase {

    private static final String MITOZA_BODY = "# Faza\n\nPo mitozie następuje mejoza.\n";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private HealthService healthService;

    @Autowired
    private LintService lintService;

    @Autowired
    private RevertService revertService;

    @Autowired
    private RunWorker worker;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private StubAIGateway gateway;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------
    // Health
    // ---------------------------------------------------------------------------

    @Test
    void theHealthViewReportsEveryStructuralFindingWithoutARun() {
        UUID kbId = insertKnowledgeBase();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        completedRun(kbId, runId -> {
            wiki.savePage(kbId, makeWrite(a, "concepts/a", "A", PageType.CONCEPT,
                "[B](/concepts/b.md), [X](/concepts/x.md) i [C](/sources/c.md).\n"), runId);
            wiki.savePage(kbId, makeWrite(b, "concepts/b", "B", PageType.CONCEPT,
                "# Faza\n\n```\n# Etapy\n```\n"), runId);
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/c", "Wspólny", PageType.CONCEPT, "C.\n"), runId);
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/d", "Wspólny", PageType.CONCEPT, "D.\n"), runId);
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "sources/lekcja", "Wspólny", PageType.SOURCE_SUMMARY,
                "S.\n"), runId);
            wiki.savePage(kbId, makeWrite(gone, "concepts/e", "E", PageType.CONCEPT, "E.\n"), runId);
        });
        UUID fenced = UUID.randomUUID();
        UUID orphaned = UUID.randomUUID();
        completedRun(kbId, runId -> {
            wiki.deletePage(kbId, gone, runId);
            wiki.addSupersessions(kbId, List.of(
                new PageSupersession(fenced, b, "etapy", a, runId, Instant.now()),
                new PageSupersession(orphaned, b, "faza", gone, runId, Instant.now()),
                new PageSupersession(UUID.randomUUID(), b, "faza", a, runId, Instant.now())));
        });
        int runsBefore = countRuns(kbId);

        KnowledgeBaseHealth health = healthService.health(kbId);

        assertThat(health.danglingLinks()).extracting(KnowledgeBaseHealth.LinkFinding::targetPath)
            .containsExactly("concepts/x", "sources/c");
        assertThat(health.wrongDirectoryLinks())
            .extracting(KnowledgeBaseHealth.LinkFinding::sourcePath, KnowledgeBaseHealth.LinkFinding::targetPath,
                KnowledgeBaseHealth.LinkFinding::livePath)
            .containsExactly(tuple("concepts/a", "sources/c", "concepts/c"));
        assertThat(health.orphanConcepts()).containsExactly("concepts/a", "concepts/c", "concepts/d");
        assertThat(health.duplicateConceptTitles()).containsExactly(
            new KnowledgeBaseHealth.DuplicateTitle("Wspólny", List.of("concepts/c", "concepts/d")));
        assertThat(health.danglingSupersessions()).extracting(KnowledgeBaseHealth.DanglingSupersession::supersessionId)
            .containsExactlyInAnyOrder(fenced, orphaned);
        assertThat(health.prefilterDue()).isFalse();
        assertThat(countRuns(kbId)).isEqualTo(runsBefore);
    }

    // ---------------------------------------------------------------------------
    // Full Lint
    // ---------------------------------------------------------------------------

    @Test
    void aFullLintInsertsLinksWithoutChangingProseStoresFindingsAndIsRevertible() {
        UUID kbId = insertKnowledgeBase();
        seedTwoConcepts(kbId);
        givenTheReviewAnswers();

        UUID lintRun = lintService.request(kbId);
        IngestRun completed = awaitRun(kbId, lintRun, RunStatus.COMPLETED);

        WikiPage mitoza = wiki.findByPath(kbId, "concepts/mitoza").orElseThrow();
        assertThat(mitoza.markdownBody()).isEqualTo("# Faza\n\nPo mitozie następuje [mejoza](/concepts/mejoza.md).\n");
        assertThat(MarkdownStructure.stripLinks(mitoza.markdownBody())).isEqualTo(MITOZA_BODY);
        assertThat(completed.findings()).containsExactly(Map.of("kind", "contradiction",
            "pages", List.of("concepts/mitoza"), "text", "Strony różnie opisują fazy."));
        assertThat(completed.stepVersions()).containsKeys("LinkChecker", "WikiReviewer");

        revertService.revert(kbId, lintRun);

        assertThat(wiki.findByPath(kbId, "concepts/mitoza")).map(WikiPage::markdownBody).contains(MITOZA_BODY);
    }

    @Test
    void aFullLintQueuesBehindAnActiveRunAndRunsWhenTheWorkerDrains() {
        UUID kbId = insertKnowledgeBase();
        seedTwoConcepts(kbId);
        givenTheReviewAnswers();
        UUID documentId = insertDocument(kbId);
        IngestRun active = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null));
        assertThat(runs.claim(kbId, active.runId())).isTrue();

        UUID lintRun = lintService.request(kbId);
        assertThatExceptionOfType(LintAlreadyQueuedException.class).isThrownBy(() -> lintService.request(kbId));
        sleep(Duration.ofMillis(300));
        assertThat(runs.findById(kbId, lintRun)).map(IngestRun::status).contains(RunStatus.QUEUED);

        transactions.executeWithoutResult(status -> runs.markWritten(kbId, active.runId(), List.of(), Map.of()));
        runs.complete(kbId, active.runId(), 0, false, List.of(), Map.of());
        worker.drain(kbId);

        awaitRun(kbId, lintRun, RunStatus.COMPLETED);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void seedTwoConcepts(UUID kbId) {
        completedRun(kbId, runId -> {
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/mitoza", "Mitoza", PageType.CONCEPT,
                MITOZA_BODY), runId);
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/mejoza", "Mejoza", PageType.CONCEPT,
                "# Gamety\n\nMejoza wytwarza gamety.\n"), runId);
        });
    }

    private void givenTheReviewAnswers() {
        gateway.reset();
        gateway.answer(Prompts.LINKS, Prompts.json(Map.of("insertions", List.of(
            Map.of("pagePath", "concepts/mitoza", "phrase", "mejoza", "targetPath", "concepts/mejoza"),
            Map.of("pagePath", "concepts/mejoza", "phrase", "Mejoza wytwarza", "targetPath", "concepts/mejoza")))));
        gateway.answer(Prompts.REVIEW, Prompts.json(Map.of("items", List.of(
            Map.of("kind", "contradiction", "pages", List.of("concepts/mitoza", "concepts/nie-ma"),
                "text", "Strony różnie opisują fazy."),
            Map.of("kind", "rewrite", "pages", List.of("concepts/mitoza"), "text", "Przepisz stronę.")))));
    }

    /** A completed ingest run whose writes commit under its lease, the way the pipeline's commits do. */
    private void completedRun(UUID kbId, Consumer<UUID> writes) {
        UUID runId = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, insertDocument(kbId), null)).runId();
        assertThat(runs.claim(kbId, runId)).isTrue();
        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, runId, List.of(), Map.of());
            writes.accept(runId);
        });
        runs.complete(kbId, runId, 0, false, List.of(), Map.of());
    }

    private IngestRun awaitRun(UUID kbId, UUID runId, RunStatus status) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            IngestRun run = runs.findById(kbId, runId).orElseThrow();
            if (run.status() == RunStatus.COMPLETED || run.status() == RunStatus.FAILED) {
                assertThat(run.status()).as("run failed with %s", run.failureReason()).isEqualTo(status);
                return run;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Run " + runId + " is still " + run.status());
            }
            sleep(Duration.ofMillis(50));
        }
    }

    private static PageWrite makeWrite(UUID pageId, String path, String title, PageType type, String body) {
        return new PageWrite(pageId, path, title, "Opis.", type, body);
    }

    private int countRuns(UUID kbId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ingest_runs WHERE knowledge_base_id = ?", Integer.class, kbId);
    }

    private UUID insertKnowledgeBase() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "lint-" + userId + "@example.com");
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, userId);
        return kbId;
    }

    private UUID insertDocument(UUID kbId) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
                + " source_filename, mime_type, upload_source, uploaded_by)"
                + " SELECT ?, kb_id, 'lekcja', 'Lekcja', ?, 'lekcja.md', 'text/markdown', 'API', owner_id"
                + " FROM knowledge_bases WHERE kb_id = ?",
            documentId, documentId.toString(), kbId);
        return documentId;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
