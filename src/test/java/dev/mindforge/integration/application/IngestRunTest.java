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
    private static final String NOTES = "# Mitoza\n\nMitoza to podział komórki.\n\n# Mejoza\n\nMejoza wytwarza gamety.\n";
    private static final String MITOZA_BODY = "# Przebieg\n\nMitoza poprzedza mejoza.\n";
    private static final String MEJOZA_BODY = "# Gamety\n\nMejoza wytwarza gamety.\n";
    private static final String SUMMARY_BODY = "# Streszczenie\n\nMitoza i mejoza.\n";
    private static final String NO_LINKS = "{\"insertions\": []}";
    private static final String LINK_TO_MEJOZA = Prompts.json(Map.of("insertions", List.of(Map.of(
        "pagePath", "concepts/mitoza", "phrase", "mejoza", "targetPath", "concepts/mejoza"))));
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
        givenTheNotesAnswers(MITOZA_BODY, MEJOZA_BODY, LINK_TO_MEJOZA);

        IngestRun run = awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.COMPLETED);

        assertThat(wiki.listIndex(kbId)).extracting(IndexEntry::path)
            .containsExactlyInAnyOrder("concepts/mitoza", "concepts/mejoza", "sources/biologia");
        assertThat(wiki.findByPath(kbId, "concepts/mitoza")).map(WikiPage::markdownBody)
            .contains("# Przebieg\n\nMitoza poprzedza [mejoza](/concepts/mejoza.md).\n");
        assertThat(wiki.pageIdsForLesson(kbId, "biologia")).hasSize(2);
        assertThat(run.stepVersions()).containsKeys("RelevanceGuard", "ClaimExtractor", "PageWriter", "LinkChecker",
            "SupersessionDetector").containsEntry("PageWriter", "1@openai/gpt-4o");
        assertThat(LogRenderer.render(runReports.logEntries(kbId)))
            .contains("* **Ingest**: [biologia](/sources/biologia.md) — 3 created.");

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

        IngestRun run = awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("unexpected error", true);
    }

    @Test
    void partialSuccessLandsAndRecordsTheFailedPageAndTheLinkToIt() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOZA_BODY, "![schemat](https://example.org/a.png)\n", LINK_TO_MEJOZA);

        IngestRun run = awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.COMPLETED);

        assertThat(wiki.listIndex(kbId)).extracting(IndexEntry::path)
            .containsExactlyInAnyOrder("concepts/mitoza", "sources/biologia");
        assertThat(wiki.findByPath(kbId, "concepts/mitoza")).map(WikiPage::markdownBody).contains(MITOZA_BODY);
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure)
            .containsEntry("step", "write").containsEntry("path", "concepts/mejoza"));
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure)
            .containsEntry("step", "linkCheck").containsEntry("dropped", 1));
    }

    @Test
    void aNewVersionWhoseDraftsAreAllUnchangedCompletesWithNoRevisionsAndNoLogLine() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOZA_BODY, MEJOZA_BODY, NO_LINKS);
        awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.COMPLETED);

        IngestRun second = awaitRun(kbId, upload(kbId, "biologia.md", NOTES + "\nPowtórka.\n", true),
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
            IntStream.range(0, 41).mapToObj(i -> claim("Twierdzenie " + i + ".", "Pojęcie " + i, 1, 1))
                .toList())));

        IngestRun run = awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("Extract returned 41 claims (limit 40)", true);
        assertThat(wiki.listIndex(kbId)).isEmpty();
    }

    @Test
    void aDocumentTheGuardRejectsFailsTheRunAsNotRetryable() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.GUARD, "{\"relevant\": false, \"reason\": \"To paragon.\", \"confidence\": 0.8}");

        IngestRun run = awaitRun(kbId, upload(kbId, "paragon.md", "Chleb 4,50 zł\n", false), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("not learning material: To paragon.", false);
    }

    @Test
    void aSupersessionIsKeptOnlyOnAShownSectionOfAPageOneLinkAway() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers("# Faza\n\nProfaza trwa godzinę. Zobacz [mejoza](/concepts/mejoza.md).\n", MEJOZA_BODY,
            NO_LINKS);
        awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.COMPLETED);

        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction(claim("Profaza trwa dwie godziny.", "Mejoza", "concepts/mejoza", 1, 1)));
        gateway.answer(Prompts.writing("concepts/mejoza"),
            draft("Podział redukcyjny.", MEJOZA_BODY + "\n# Profaza\n\nProfaza trwa dwie godziny.\n"));
        gateway.answer(Prompts.writing("sources/genetyka"), draft("Lekcja genetyki.", SUMMARY_BODY));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, Prompts.json(Map.of("proposals", List.of(
            Map.of("supersededPath", "concepts/mitoza", "sectionAnchor", "faza", "supersedingPath", "concepts/mejoza"),
            Map.of("supersededPath", "sources/biologia", "sectionAnchor", "streszczenie",
                "supersedingPath", "concepts/mejoza")))));

        IngestRun run = awaitRun(kbId, upload(kbId, "genetyka.md", "# Profaza\n\nProfaza trwa dwie godziny.\n", false),
            RunStatus.COMPLETED);

        assertThat(run.supersessionCount()).isEqualTo(1);
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure)
            .containsEntry("step", "supersede").containsEntry("dropped", 1));
        UUID mitoza = wiki.findByPath(kbId, "concepts/mitoza").orElseThrow().pageId();
        assertThat(wiki.liveSupersessionsOf(kbId, List.of(mitoza)))
            .extracting(LiveSupersession::sectionAnchor, LiveSupersession::supersedingPath)
            .containsExactly(tuple("faza", "concepts/mejoza"));
    }

    // ---------------------------------------------------------------------------
    // Conversation edits
    // ---------------------------------------------------------------------------

    @Test
    void aConversationEditDeletesAndRetitlesLiveConceptsAndDropsWhatItMayNotChange() {
        UUID kbId = insertKnowledgeBase();
        givenTheNotesAnswers(MITOZA_BODY, MEJOZA_BODY, NO_LINKS);
        awaitRun(kbId, upload(kbId, "biologia.md", NOTES, false), RunStatus.COMPLETED);
        gateway.reset();
        gateway.answer(Prompts.EDIT, Prompts.json(Map.of("items", List.of(
            Map.of("kind", "delete", "path", "concepts/mejoza"),
            Map.of("kind", "delete", "path", "sources/biologia"),
            Map.of("kind", "retitle", "path", "concepts/mitoza", "title", "Mitoza komórkowa")))));

        IngestRun run = awaitRun(kbId, edit(kbId, "Usuń mejozę i nazwij mitozę pełniej."), RunStatus.COMPLETED);

        assertThat(wiki.findByPath(kbId, "concepts/mejoza")).isEmpty();
        assertThat(wiki.findByPath(kbId, "sources/biologia")).isPresent();
        assertThat(wiki.findByPath(kbId, "concepts/mitoza")).get()
            .extracting(WikiPage::title, WikiPage::markdownBody).containsExactly("Mitoza komórkowa", MITOZA_BODY);
        assertThat(run.failures()).anySatisfy(failure -> assertThat(failure)
            .containsEntry("item", "delete").containsEntry("path", "sources/biologia"));
        assertThat(LogRenderer.render(runReports.logEntries(kbId)))
            .contains("* **Edit**: conversation — 1 revised, 1 deleted.");
    }

    @Test
    void aConversationEditThatNamesNoPageFailsAsNotRetryable() {
        UUID kbId = insertKnowledgeBase();
        gateway.reset();
        gateway.answer(Prompts.EDIT, "{\"items\": []}");

        IngestRun run = awaitRun(kbId, edit(kbId, "Popraw to."), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("edit named no page", false);
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
        gateway.answer("] Pierwsza lekcja", prompt -> {
            extracting.countDown();
            await(release);
            return extraction();
        });
        gateway.answer(Prompts.EXTRACT, extraction());
        gateway.answer(Prompts.WRITE, draft("Lekcja.", SUMMARY_BODY));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");

        UUID first = upload(kbA, "lekcja-1.md", "Pierwsza lekcja o komórkach.\n", false);
        assertThat(extracting.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        UUID second = upload(kbA, "lekcja-2.md", "Druga lekcja o komórkach.\n", false);
        UUID elsewhere = upload(kbB, "lekcja-1.md", "Lekcja w innej bazie.\n", false);

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
            return draft("Lekcja.", SUMMARY_BODY);
        });

        UUID documentId = upload(kbId, "biologia.md", NOTES, false);

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
    private void givenTheNotesAnswers(String mitozaBody, String mejozaBody, String links) {
        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction(
            claim("Mitoza to podział komórki.", "Mitoza", 0, 1),
            claim("Mejoza wytwarza gamety.", "Mejoza", 2, 3)));
        gateway.answer(Prompts.writing("concepts/mitoza"), draft("Podział komórki.", mitozaBody));
        gateway.answer(Prompts.writing("concepts/mejoza"), draft("Podział redukcyjny.", mejozaBody));
        gateway.answer(Prompts.writing("sources/biologia"), draft("Notatki z biologii.", SUMMARY_BODY));
        gateway.answer(Prompts.LINKS, links);
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    private static String extraction(Map<?, ?>... claims) {
        return Prompts.json(Map.of("claims", List.of(claims), "chunkDigest", "Mitoza i mejoza."));
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
