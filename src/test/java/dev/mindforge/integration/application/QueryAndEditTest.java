package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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

import dev.mindforge.application.service.FlashcardService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.SearchService;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.TurnSummary;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.InteractionStore;
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
class QueryAndEditTest extends TestContainerBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private SearchService search;

    @Autowired
    private FlashcardService flashcards;

    @Autowired
    private InteractionStore interactions;

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

    @Test
    void aUsersHistoryHoldsOnlyTheirQuestionsAndAnswers() {
        UUID kbId = insertKnowledgeBase();
        UUID owner = ownerOf(kbId);
        UUID other = insertUser();
        Interaction mine = interactions.createInteraction(kbId, new Interaction(UUID.randomUUID(), kbId, owner, Instant.now()));
        Interaction theirs = interactions.createInteraction(kbId, new Interaction(UUID.randomUUID(), kbId, other, Instant.now()));
        interactions.addTurn(kbId, mine.interactionId(), new InteractionTurn(UUID.randomUUID(), "First?", "Yes.",
            List.of("concepts/mitosis"), Instant.now().minusSeconds(5)));
        interactions.addTurn(kbId, mine.interactionId(), new InteractionTurn(UUID.randomUUID(), "Second?", "No.",
            List.of(), Instant.now()));
        interactions.addTurn(kbId, theirs.interactionId(), new InteractionTurn(UUID.randomUUID(), "Someone else's?", "Yes.",
            List.of(), Instant.now()));

        assertThat(interactions.listForUser(kbId, owner))
            .containsExactly(new TurnSummary("Second?", "No."), new TurnSummary("First?", "Yes."));
        assertThat(interactions.recentTurns(kbId, mine.interactionId(), 5).getFirst().usedPagePaths())
            .containsExactly("concepts/mitosis");
    }

    @Test
    void recentTurnsReadsOnlyTheLastNOldestFirst() {
        UUID kbId = insertKnowledgeBase();
        Interaction session = interactions.createInteraction(kbId,
            new Interaction(UUID.randomUUID(), kbId, ownerOf(kbId), Instant.now()));
        Instant start = Instant.now().minusSeconds(100);
        for (int i = 0; i < 20; i++) {
            interactions.addTurn(kbId, session.interactionId(), new InteractionTurn(UUID.randomUUID(), "Q" + i, "A",
                List.of(), start.plusSeconds(i)));
        }

        assertThat(interactions.recentTurns(kbId, session.interactionId(), 5)).extracting(InteractionTurn::question)
            .containsExactly("Q15", "Q16", "Q17", "Q18", "Q19");
    }

    @Test
    void pageSearchMatchesTitlesDescriptionsAndBodiesInOneKnowledgeBase() {
        UUID kbId = insertKnowledgeBase();
        UUID otherKb = insertKnowledgeBase();
        completedRun(kbId, runId -> {
            wiki.savePage(kbId, makeWrite("concepts/mitosis", "Mitosis", "Nuclear division.", "Content."), runId);
            wiki.savePage(kbId, makeWrite("concepts/meiosis", "Meiosis", "Reductive division 100%.", "Gametes."), runId);
            wiki.savePage(kbId, makeWrite("concepts/dna", "DNA", "Acid.", "Contains chromosomes and genes."), runId);
        });
        completedRun(otherKb, runId -> wiki.savePage(otherKb, makeWrite("concepts/mitosis", "Mitosis", "Foreign.", "X."), runId));

        assertThat(search.search(kbId, "mito")).extracting(IndexEntry::path).containsExactly("concepts/mitosis");
        assertThat(search.search(kbId, "reductive")).extracting(IndexEntry::path).containsExactly("concepts/meiosis");
        assertThat(search.search(kbId, "chromosomes")).extracting(IndexEntry::path).containsExactly("concepts/dna");
        assertThat(search.search(kbId, "100%")).extracting(IndexEntry::path).containsExactly("concepts/meiosis");
        assertThat(search.search(kbId, "m")).isEmpty();
    }

    @Test
    void aConversationEditIsAnUndeduplicatedDocumentWhoseRunChangesTheWiki() {
        UUID kbId = insertKnowledgeBase();
        completedRun(kbId, runId -> wiki.savePage(kbId, makeWrite("concepts/mitosis", "Mitosis", "Description.", "# Phase\n\nA.\n"),
            runId));
        gateway.reset();
        gateway.answer(Prompts.EDIT, "{\"items\": [{\"kind\": \"delete\", \"path\": \"concepts/mitosis\"}]}");

        IngestRun first = awaitRun(kbId, ingestion.submitEdit(kbId, ownerOf(kbId), "Delete mitosis.", null));
        IngestRun second = awaitRun(kbId, ingestion.submitEdit(kbId, ownerOf(kbId), "Delete mitosis.", null));

        assertThat(first.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(wiki.findByPath(kbId, "concepts/mitosis")).isEmpty();
        assertThat(second).extracting(IngestRun::status, IngestRun::failureReason, IngestRun::retryable)
            .containsExactly(RunStatus.FAILED, "no applicable change", false);
        assertThat(jdbc.queryForList("SELECT lesson_id, upload_source, content_hash FROM documents"
            + " WHERE knowledge_base_id = ?", kbId)).hasSize(3)
            .filteredOn(row -> "conversation".equals(row.get("lesson_id"))).hasSize(2)
            .allMatch(row -> "CONVERSATION".equals(row.get("upload_source")));

        gateway.reset();
        gateway.answer(Prompts.EDIT, "{\"items\": []}");
        assertThat(awaitRun(kbId, ingestion.submitEdit(kbId, ownerOf(kbId), "Fix this.", null)))
            .extracting(IngestRun::failureReason, IngestRun::retryable).containsExactly("edit named no page", false);
        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> flashcards.deck(kbId, new StudyScope.Lesson("conversation")));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private IngestRun awaitRun(UUID kbId, UUID runId) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            IngestRun run = runs.findById(kbId, runId).orElseThrow();
            if (run.status() == RunStatus.COMPLETED || run.status() == RunStatus.FAILED) {
                return run;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Run " + runId + " is still " + run.status());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static PageWrite makeWrite(String path, String title, String description, String body) {
        return new PageWrite(UUID.randomUUID(), path, title, description, PageType.CONCEPT, body);
    }

    private void completedRun(UUID kbId, Consumer<UUID> writes) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
            + " source_filename, mime_type, upload_source, uploaded_by) SELECT ?, kb_id, 'lesson', 'Lesson', ?,"
            + " 'lesson.md', 'text/markdown', 'API', owner_id FROM knowledge_bases WHERE kb_id = ?",
            documentId, documentId.toString(), kbId);
        UUID runId = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null)).runId();
        assertThat(runs.claim(kbId, runId)).isTrue();
        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, runId, List.of(), Map.of());
            writes.accept(runId);
        });
        runs.complete(kbId, runId, 0, false, List.of(), Map.of());
    }

    private UUID ownerOf(UUID kbId) {
        return jdbc.queryForObject("SELECT owner_id FROM knowledge_bases WHERE kb_id = ?", UUID.class, kbId);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "query-" + userId + "@example.com");
        return userId;
    }

    private UUID insertKnowledgeBase() {
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, insertUser());
        return kbId;
    }
}
