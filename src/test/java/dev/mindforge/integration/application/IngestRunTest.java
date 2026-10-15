package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.agent.ClaimExtractor;
import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.PageWriter;
import dev.mindforge.agent.RelevanceGuard;
import dev.mindforge.agent.SupersessionDetector;
import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.RevertService;
import dev.mindforge.application.wiki.LogRenderer;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.ConversationTurn;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.RunFailure;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.parsing.MarkdownParser;
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
class IngestRunTest extends TestContainerBase {

    /** Blocks 0–3: heading, text, heading, text. */
    private static final String NOTES = "# Mitosis\n\nMitosis is cell division.\n\n# Meiosis\n\nMeiosis makes gametes.\n";
    private static final String MITOSIS_BODY = "# Course\n\nMitosis precedes meiosis.\n";
    private static final String MEIOSIS_BODY = "# Gametes\n\nMeiosis makes gametes.\n";
    private static final String SUMMARY_BODY = "# Summary\n\nMitosis and meiosis.\n";
    private static final String NO_LINKS = "{\"insertions\": []}";
    private static final String LINK_TO_MEIOSIS = Prompts.json(Map.of("insertions", List.of(Map.of(
        "pagePath", "concepts/mitosis", "phrase", "meiosis", "targetPath", "concepts/meiosis"))));
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private RunReportQuery runReports;

    @Autowired
    private RevertService revertService;

    @Autowired
    private EventPublisher events;

    @Autowired
    private StubAIGateway gateway;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------
    // Document ingest
    // ---------------------------------------------------------------------------

    @Test
    void aDocumentIngestsIntoLinkedPagesEndToEndAndARevertRemovesThem() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOSIS_BODY, MEIOSIS_BODY, LINK_TO_MEIOSIS);

        IngestRun run = awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);

        assertThat(wiki.listIndex(kbId)).extracting(IndexEntry::path)
            .containsExactlyInAnyOrder("concepts/mitosis", "concepts/meiosis", "sources/biology");
        assertThat(wiki.findByPath(kbId, "concepts/mitosis")).map(WikiPage::markdownBody)
            .contains("# Course\n\nMitosis precedes [meiosis](/concepts/meiosis.md).\n");
        assertThat(wiki.pageIdsForLesson(kbId, "biology")).hasSize(2);
        assertThat(run.stepVersions()).containsOnlyKeys("RelevanceGuard", "ClaimExtractor", "PageWriter",
            "LinkChecker", "SupersessionDetector");
        assertThat(run.stepVersions().get("RelevanceGuard")).startsWith(RelevanceGuard.VERSION + "@");
        assertThat(run.stepVersions().get("ClaimExtractor")).startsWith(ClaimExtractor.VERSION + "@");
        assertThat(run.stepVersions().get("PageWriter")).isEqualTo(PageWriter.VERSION + "@openai/gpt-4o");
        assertThat(run.stepVersions().get("LinkChecker")).startsWith(LinkChecker.VERSION + "@");
        assertThat(run.stepVersions().get("SupersessionDetector")).startsWith(SupersessionDetector.VERSION + "@");
        assertThat(LogRenderer.render(runReports.logEntries(kbId)))
            .contains("* **Ingest**: [biology](/sources/biology.md) — 3 created.");

        revertService.revert(kbId, run.runId());

        assertThat(wiki.listIndex(kbId)).isEmpty();
    }

    @Test
    void anUnexpectedErrorFailsTheRunWithoutRecordingItsMessage() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.GUARD, prompt -> {
            throw new IllegalStateException("connection to db-internal:5432 refused");
        });

        IngestRun run = awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("unexpected error", true);
    }

    @Test
    void aSecondLessonWithATakenTitleCarriesItsIdSoCitationsDoNotReadAlike() {
        UUID kbId = insertKnowledgeBase();
        UUID first = upload(kbId, "mitosis.md", NOTES, false);
        UUID second = ingestion.ingest(new DocumentUpload(kbId, ownerOf(kbId), UploadSource.API, "mitosis.md",
            MarkdownParser.MIME_TYPE, (NOTES + "\nSecond version.\n").getBytes(StandardCharsets.UTF_8), "mitosis-2",
            false));

        assertThat(documents.findById(kbId, first)).get().extracting(d -> d.lessonIdentity().title()).isEqualTo("mitosis");
        assertThat(documents.findById(kbId, second)).get().extracting(d -> d.lessonIdentity().title())
            .isEqualTo("mitosis (mitosis-2)");
        assertThat(documents.findById(kbId, second)).get().extracting(d -> d.lessonIdentity().lessonId()).isEqualTo("mitosis-2");
    }

    @Test
    void aDocumentWhoseEveryRunFailedCanBeRemovedAndUploadedAgain() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.GUARD, prompt -> {
            throw new IllegalStateException("provider down");
        });
        UUID documentId = upload(kbId, "biology.md", NOTES, false);
        awaitRun(kbId, documentId, RunStatus.FAILED);

        ingestion.remove(kbId, documentId);

        assertThat(documents.findById(kbId, documentId)).isEmpty();
        assertThat(runs.latestForDocument(kbId, documentId)).isEmpty();
        assertThat(upload(kbId, "biology.md", NOTES, false)).isNotEqualTo(documentId);
    }

    @Test
    void aRetryThatCompletesDropsTheFailedAttemptsItGotPast() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.GUARD, prompt -> {
            throw new IllegalStateException("provider down");
        });
        UUID documentId = upload(kbId, "biology.md", NOTES, false);
        awaitRun(kbId, documentId, RunStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingest_runs WHERE document_id = ?", Integer.class,
            documentId)).isEqualTo(1);

        givenTheNotesAnswers(MITOSIS_BODY, MEIOSIS_BODY, LINK_TO_MEIOSIS);
        ingestion.retry(kbId, documentId);
        awaitRun(kbId, documentId, RunStatus.COMPLETED);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingest_runs WHERE document_id = ?", Integer.class,
            documentId)).as("only the run that got past the failure is kept").isEqualTo(1);
    }

    @Test
    void aDocumentThatWrotePagesCannotBeRemoved() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOSIS_BODY, MEIOSIS_BODY, LINK_TO_MEIOSIS);
        UUID documentId = upload(kbId, "biology.md", NOTES, false);
        awaitRun(kbId, documentId, RunStatus.COMPLETED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ingestion.remove(kbId, documentId))
            .isInstanceOf(dev.mindforge.domain.model.DocumentNotRemovableException.class);
        assertThat(documents.findById(kbId, documentId)).isPresent();
    }

    @Test
    void partialSuccessLandsAndRecordsTheFailedPageAndTheLinkToIt() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOSIS_BODY, "![diagram](https://example.org/a.png)\n", LINK_TO_MEIOSIS);

        IngestRun run = awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);

        assertThat(wiki.listIndex(kbId)).extracting(IndexEntry::path)
            .containsExactlyInAnyOrder("concepts/mitosis", "sources/biology");
        assertThat(wiki.findByPath(kbId, "concepts/mitosis")).map(WikiPage::markdownBody).contains(MITOSIS_BODY);
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure).extracting(RunFailure::step, RunFailure::path).containsExactly("write", "concepts/meiosis"));
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure).extracting(RunFailure::step, RunFailure::dropped).containsExactly("linkCheck", 1));
    }

    @Test
    void aRowWrittenBeforeFailuresWereTypedStillReadsBack() {
        UUID kbId = insertKnowledgeBase();
        IngestRun run = awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);
        jdbc.update("UPDATE ingest_runs SET failures = '[{\"step\":\"write\",\"path\":\"concepts/a\",\"reason\":\"x\"},"
            + "{\"step\":\"supersede\",\"omittedPages\":3}]'::jsonb WHERE run_id = ?", run.runId());

        assertThat(runs.findById(kbId, run.runId()).orElseThrow().failures()).containsExactly(
            RunFailure.ofPath("write", "concepts/a", "x"), RunFailure.omittedPages("supersede", 3));
    }

    @Test
    void aNewVersionWhoseDraftsAreAllUnchangedCompletesWithNoRevisionsAndNoLogLine() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOSIS_BODY, MEIOSIS_BODY, NO_LINKS);
        awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);

        IngestRun second = awaitRun(kbId, upload(kbId, "biology.md", NOTES + "\nRevision.\n", true),
            RunStatus.COMPLETED);

        assertThat(wiki.revisionsByRun(kbId, second.runId())).isEmpty();
        assertThat(LogRenderer.render(runReports.logEntries(kbId))).containsOnlyOnce("**Ingest**");
    }

    @Test
    void anExtractCallOverTheClaimCapFailsTheRunAsRetryable() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, Prompts.json(Map.of("claims",
            IntStream.range(0, 41).mapToObj(i -> claim("Claim " + i + ".", "Concept " + i, 1, 1))
                .toList())));

        IngestRun run = awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("Extract returned 41 claims (limit 40)", true);
        assertThat(wiki.listIndex(kbId)).isEmpty();
    }

    @Test
    void aDocumentTheGuardRejectsFailsTheRunAsNotRetryable() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.GUARD, "{\"relevant\": false, \"reason\": \"It is a receipt.\", \"confidence\": 0.8}");

        IngestRun run = awaitRun(kbId, upload(kbId, "receipt.md", "Bread 4.50 USD\n", false), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("not learning material: It is a receipt.", false);
    }

    @Test
    void aSupersessionIsKeptOnlyOnAShownSectionOfAPageOneLinkAway() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers("# Phase\n\nProphase takes an hour. See [meiosis](/concepts/meiosis.md).\n", MEIOSIS_BODY,
            NO_LINKS);
        awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);

        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction(claim("Prophase takes two hours.", "Meiosis", "concepts/meiosis", 1, 1)));
        gateway.answer(Prompts.writing("concepts/meiosis"),
            draft("Reductive division.", MEIOSIS_BODY + "\n# Prophase\n\nProphase takes two hours.\n"));
        gateway.answer(Prompts.writing("sources/genetics"), draft("Genetics lesson.", SUMMARY_BODY));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, Prompts.json(Map.of("proposals", List.of(
            Map.of("supersededPath", "concepts/mitosis", "sectionAnchor", "phase", "supersedingPath", "concepts/meiosis"),
            Map.of("supersededPath", "sources/biology", "sectionAnchor", "summary",
                "supersedingPath", "concepts/meiosis")))));

        IngestRun run = awaitRun(kbId, upload(kbId, "genetics.md", "# Prophase\n\nProphase takes two hours.\n", false),
            RunStatus.COMPLETED);

        assertThat(run.supersessionCount()).isEqualTo(1);
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure).extracting(RunFailure::step, RunFailure::dropped).containsExactly("supersede", 1));
        UUID mitosis = wiki.findByPath(kbId, "concepts/mitosis").orElseThrow().pageId();
        assertThat(wiki.liveSupersessionsOf(kbId, List.of(mitosis)))
            .extracting(LiveSupersession::sectionAnchor, LiveSupersession::supersedingPath)
            .containsExactly(tuple("phase", "concepts/meiosis"));
    }

    // ---------------------------------------------------------------------------
    // Conversation edits
    // ---------------------------------------------------------------------------

    @Test
    void aConversationEditDeletesAndRetitlesLiveConceptsAndDropsWhatItMayNotChange() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOSIS_BODY, MEIOSIS_BODY, NO_LINKS);
        awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);
        gateway.reset();
        gateway.answer(Prompts.EDIT, Prompts.json(Map.of("items", List.of(
            Map.of("kind", "delete", "path", "concepts/meiosis"),
            Map.of("kind", "delete", "path", "sources/biology"),
            Map.of("kind", "retitle", "path", "concepts/mitosis", "title", "Cell mitosis")))));

        IngestRun run = awaitRun(kbId, edit(kbId, "Delete meiosis and give mitosis a fuller name."), RunStatus.COMPLETED);

        assertThat(wiki.findByPath(kbId, "concepts/meiosis")).isEmpty();
        assertThat(wiki.findByPath(kbId, "sources/biology")).isPresent();
        assertThat(wiki.findByPath(kbId, "concepts/mitosis")).get()
            .extracting(WikiPage::title, WikiPage::markdownBody).containsExactly("Cell mitosis", MITOSIS_BODY);
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure).extracting(RunFailure::item, RunFailure::path).containsExactly("delete", "sources/biology"));
        assertThat(LogRenderer.render(runReports.logEntries(kbId)))
            .contains("* **Edit**: conversation — 1 revised, 1 deleted.");
    }

    @Test
    void aConversationEditThatNamesNoPageFailsAsNotRetryable() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.EDIT, "{\"items\": []}");

        IngestRun run = awaitRun(kbId, edit(kbId, "Fix this."), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("edit named no page", false);
    }

    @Test
    void aConversationEditThatOnlyRetitlesToTheCurrentTitleFailsAsNotRetryable() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOSIS_BODY, MEIOSIS_BODY, NO_LINKS);
        awaitRun(kbId, upload(kbId, "biology.md", NOTES, false), RunStatus.COMPLETED);
        String title = wiki.findByPath(kbId, "concepts/mitosis").orElseThrow().title();
        gateway.reset();
        gateway.answer(Prompts.EDIT, Prompts.json(Map.of("items", List.of(
            Map.of("kind", "retitle", "path", "concepts/mitosis", "title", title)))));

        IngestRun run = awaitRun(kbId, edit(kbId, "Keep the mitosis name."), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("no applicable change", false);
    }

    // ---------------------------------------------------------------------------
    // Queue, lease and fencing
    // ---------------------------------------------------------------------------

    @Test
    void uploadsIntoOneKnowledgeBaseSerializeWhileAnotherKnowledgeBaseRunsConcurrently() throws Exception {
        UUID kbA = insertKnowledgeBase();
        UUID kbB = insertKnowledgeBase();
        CountDownLatch extracting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer("] First lesson", prompt -> {
            extracting.countDown();
            await(release);
            return extraction();
        });
        gateway.answer(Prompts.EXTRACT, extraction());
        gateway.answer(Prompts.WRITE, draft("Lesson.", SUMMARY_BODY));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");

        UUID first = upload(kbA, "lesson-1.md", "First lesson about cells.\n", false);
        assertThat(extracting.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        UUID second = upload(kbA, "lesson-2.md", "Second lesson about cells.\n", false);
        UUID elsewhere = upload(kbB, "lesson-1.md", "Lesson in a different knowledge base.\n", false);

        awaitRun(kbB, elsewhere, RunStatus.COMPLETED);
        assertThat(runs.latestForDocument(kbA, second)).map(IngestRun::status).contains(RunStatus.QUEUED);

        release.countDown();
        awaitRun(kbA, first, RunStatus.COMPLETED);
        awaitRun(kbA, second, RunStatus.COMPLETED);
    }

    @Test
    void aRunSweptWhileGeneratingCannotCommit() throws Exception {
        UUID kbId = insertKnowledgeBase();
        CountDownLatch committing = new CountDownLatch(1);
        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction());
        gateway.answer(Prompts.WRITE, prompt -> {
            // What another instance's sweep does to a run it believes was interrupted.
            jdbc.update("UPDATE ingest_runs SET status = 'FAILED', finished_at = now()"
                + " WHERE knowledge_base_id = ? AND status = 'RUNNING'", kbId);
            jdbc.update("UPDATE knowledge_bases SET active_run_id = NULL WHERE kb_id = ?", kbId);
            committing.countDown();
            return draft("Lesson.", SUMMARY_BODY);
        });

        UUID documentId = upload(kbId, "biology.md", NOTES, false);

        assertThat(committing.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < until) {
            assertThat(wiki.listIndex(kbId)).isEmpty();
            Thread.sleep(100);
        }
        assertThat(runs.latestForDocument(kbId, documentId)).get()
            .extracting(IngestRun::status, IngestRun::failureReason).containsExactly(RunStatus.FAILED, null);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** The guard, an extraction of two claims from {@link #NOTES}, and the three drafts and links; no supersessions. */
    private void givenTheNotesAnswers(String mitosisBody, String meiosisBody, String links) {
        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction(
            claim("Mitosis is cell division.", "Mitosis", 0, 1),
            claim("Meiosis makes gametes.", "Meiosis", 2, 3)));
        gateway.answer(Prompts.writing("concepts/mitosis"), draft("Cell division.", mitosisBody));
        gateway.answer(Prompts.writing("concepts/meiosis"), draft("Reductive division.", meiosisBody));
        gateway.answer(Prompts.writing("sources/biology"), draft("Biology notes.", SUMMARY_BODY));
        gateway.answer(Prompts.LINKS, links);
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    private static String extraction(Map<?, ?>... claims) {
        return Prompts.json(Map.of("claims", List.of(claims), "chunkDigest", "Mitosis and meiosis."));
    }

    private static Map<String, Object> claim(String text, String title, int firstBlock, int lastBlock) {
        return claim(text, title, null, firstBlock, lastBlock);
    }

    private static Map<String, Object> claim(String text, String title, String targetPath, int firstBlock,
                                             int lastBlock) {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("text", text);
        claim.put("title", title);
        claim.put("targetPath", targetPath);
        claim.put("firstBlock", firstBlock);
        claim.put("lastBlock", lastBlock);
        return claim;
    }

    private static String draft(String description, String body) {
        return Prompts.json(Map.of("description", description, "body", body));
    }

    private UUID upload(UUID kbId, String filename, String markdown, boolean newVersion) {
        return ingestion.ingest(new DocumentUpload(kbId, ownerOf(kbId), UploadSource.API, filename,
            MarkdownParser.MIME_TYPE, markdown.getBytes(StandardCharsets.UTF_8), null, newVersion));
    }

    /** A conversation turn queued exactly as an upload is. */
    private UUID edit(UUID kbId, String instruction) {
        String content = new ConversationTurn(instruction, null).content();
        return transactions.execute(status -> {
            Document turn = documents.insert(kbId, new Document(UUID.randomUUID(), kbId,
                new LessonIdentity("conversation", "Conversation"),
                ContentHash.compute(content.getBytes(StandardCharsets.UTF_8)), "conversation", "text/plain", content,
                List.of(ContentBlock.text(content, 0)), UploadSource.CONVERSATION, ownerOf(kbId), null, null));
            IngestRun run = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, turn.documentId(), null));
            events.publish(new DomainEvent.IngestRunQueued(run.runId(), kbId, Instant.now()));
            return turn.documentId();
        });
    }

    private IngestRun awaitRun(UUID kbId, UUID documentId, RunStatus status) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            Optional<IngestRun> run = runs.latestForDocument(kbId, documentId);
            if (run.isPresent() && Set.of(RunStatus.COMPLETED, RunStatus.FAILED).contains(run.get().status())) {
                assertThat(run.get().status()).as("run failed with %s", run.get().failureReason()).isEqualTo(status);
                return run.get();
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Run of " + documentId + " is still " + run.map(IngestRun::status));
            }
            await(Duration.ofMillis(50));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID ownerOf(UUID kbId) {
        return jdbc.queryForObject("SELECT owner_id FROM knowledge_bases WHERE kb_id = ?", UUID.class, kbId);
    }

    private UUID insertKnowledgeBase() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "ingest-" + userId + "@example.com");
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, userId);
        return kbId;
    }
}
