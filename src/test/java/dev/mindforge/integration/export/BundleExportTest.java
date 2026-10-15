package dev.mindforge.integration.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.export.BundleConformanceValidator;
import dev.mindforge.infrastructure.export.BundleExporter;
import dev.mindforge.infrastructure.export.BundleNotConformantException;
import dev.mindforge.support.ExportedBundle;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class BundleExportTest extends TestContainerBase {

    private static final String TITLE = "Mitosis: \"division\" [cell]";

    @Autowired
    private BundleExporter exporter;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anExportOfEveryKindOfRowIsConformantEscapedAndCarriesNoRunInternals() {
        UUID kbId = insertKnowledgeBase();
        UUID lesson = insertDocument(kbId, "bio-1", "First\nlesson", "API");
        UUID turn = insertDocument(kbId, "conversation", "Conversation", "CONVERSATION");
        UUID mitosis = UUID.randomUUID();
        UUID meiosis = UUID.randomUUID();
        UUID summary = UUID.randomUUID();
        UUID ingest = completedRun(kbId, lesson, runId -> {
            wiki.savePage(kbId, new PageWrite(mitosis, "concepts/mitosis", TITLE, "Description with \"quotes\": yes.",
                PageType.CONCEPT, "# Phase\n\nProphase.\n"), runId);
            wiki.savePage(kbId, new PageWrite(meiosis, "concepts/meiosis", "Meiosis", "Description.", PageType.CONCEPT,
                "# Gametes\n\nGametes.\n"), runId);
            wiki.savePage(kbId, new PageWrite(summary, "sources/bio-1", "First lesson", "Summary.",
                PageType.SOURCE_SUMMARY, "# Summary\n\nContent.\n"), runId);
            wiki.addSources(kbId, List.of(new PageSource(mitosis, lesson, runId), new PageSource(summary, lesson, runId)));
            wiki.addSupersessions(kbId, List.of(
                new PageSupersession(UUID.randomUUID(), mitosis, "phase", meiosis, runId, Instant.now())));
        });
        completedRun(kbId, turn, runId -> wiki.addSources(kbId, List.of(new PageSource(meiosis, turn, runId))));
        jdbc.update("UPDATE ingest_runs SET failures = '[{\"reason\":\"SEKRET-FAILURE\"}]'::jsonb,"
            + " findings = '[{\"text\":\"SEKRET-FINDING\"}]'::jsonb, step_versions = '{\"PageWriter\":\"SEKRET\"}'::jsonb,"
            + " cost = 12.5 WHERE run_id = ?", ingest);

        Map<String, String> files = ExportedBundle.files(exporter, kbId, "Biology: basics");

        assertThat(BundleExporter.root("Biology: basics")).isEqualTo("biology-basics-okf");
        assertThat(files).containsOnlyKeys("index.md", "log.md", "concepts/meiosis.md", "concepts/mitosis.md",
            "sources/bio-1.md");
        assertThat(BundleConformanceValidator.violations(files)).isEmpty();
        Map<String, Object> frontmatter = new Yaml().load(files.get("concepts/mitosis.md").split("---\n")[1]);
        assertThat(frontmatter).containsEntry("type", "Concept").containsEntry("title", TITLE)
            .containsEntry("description", "Description with \"quotes\": yes.").containsKey("timestamp");
        assertThat(files.get("index.md")).contains("* [Mitosis: \"division\" \\[cell\\]](/concepts/mitosis.md)");
        assertThat(files.get("concepts/mitosis.md"))
            .contains("# Phase\n\n> Superseded by [Meiosis](/concepts/meiosis.md).\n")
            .endsWith("# Citations\n\n[1] [First lesson](/sources/bio-1.md)\n");
        assertThat(files.get("concepts/meiosis.md")).containsPattern("\\[1\\] Conversation, \\d{4}-\\d{2}-\\d{2}");
        assertThat(files.get("sources/bio-1.md")).contains("[1] notes.md, uploaded ");
        assertThat(files.get("log.md")).contains("**Ingest**");
        assertThat(files.values()).noneMatch(content -> content.contains("SEKRET") || content.contains("12.5"));
    }

    @Test
    void anEmptyKnowledgeBaseExportsAValidBundle() {
        Map<String, String> files = ExportedBundle.files(exporter, insertKnowledgeBase(), "細胞");

        assertThat(files).containsOnlyKeys("index.md", "log.md");
        assertThat(BundleConformanceValidator.violations(files)).isEmpty();
        assertThat(BundleExporter.root("細胞")).matches("x-[0-9a-f]{8}-okf");
    }

    @Test
    void anIngestCommittedDuringAnExportDoesNotTearTheBundle() throws Exception {
        UUID kbId = insertKnowledgeBase();
        UUID lesson = insertDocument(kbId, "bio-1", "Lesson", "API");
        TransactionTemplate snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        Map<String, String> files = snapshot.execute(status -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM wiki_pages WHERE knowledge_base_id = ?", Integer.class, kbId);
            try (ExecutorService other = Executors.newSingleThreadExecutor()) {
                other.submit(() -> completedRun(kbId, lesson, runId -> wiki.savePage(kbId, new PageWrite(UUID.randomUUID(),
                    "concepts/mitosis", "Mitosis", "Description.", PageType.CONCEPT, "Content.\n"), runId))).get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return ExportedBundle.files(exporter, kbId, "Biology");
        });

        assertThat(files).containsOnlyKeys("index.md", "log.md");
        assertThat(files.get("index.md")).doesNotContain("mitosis");
        assertThat(files.get("log.md")).doesNotContain("Ingest");
        assertThat(ExportedBundle.files(exporter, kbId, "Biology")).containsKey("concepts/mitosis.md");
    }

    @Test
    void aNonConformantBundleIsRejectedBeforeAByteIsWritten() {
        UUID kbId = insertKnowledgeBase();
        UUID lesson = insertDocument(kbId, "bio-1", "Lesson", "API");
        completedRun(kbId, lesson, runId -> wiki.savePage(kbId, new PageWrite(UUID.randomUUID(), "concepts/log",
            "Log", "Description.", PageType.CONCEPT, "Content."), runId));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatThrownBy(() -> exporter.export(kbId, "Biology", out))
            .isInstanceOf(BundleNotConformantException.class);
        assertThat(out.size()).isZero();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** A completed ingest run whose writes commit under its lease, the way the pipeline's commits do. */
    private UUID completedRun(UUID kbId, UUID documentId, Consumer<UUID> writes) {
        UUID runId = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null)).runId();
        assertThat(runs.claim(kbId, runId)).isTrue();
        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, runId, List.of(), Map.of());
            writes.accept(runId);
        });
        runs.complete(kbId, runId, 0, false, List.of(), Map.of());
        return runId;
    }

    private UUID insertKnowledgeBase() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "export-" + userId + "@example.com");
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, userId);
        return kbId;
    }

    private UUID insertDocument(UUID kbId, String lessonId, String lessonTitle, String uploadSource) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
                + " source_filename, mime_type, upload_source, uploaded_by)"
                + " SELECT ?, kb_id, ?, ?, ?, 'notes.md', 'text/markdown', ?, owner_id FROM knowledge_bases"
                + " WHERE kb_id = ?",
            documentId, lessonId, lessonTitle, documentId.toString(), uploadSource, kbId);
        return documentId;
    }
}
