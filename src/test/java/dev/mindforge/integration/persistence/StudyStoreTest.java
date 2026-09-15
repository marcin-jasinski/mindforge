package dev.mindforge.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.PageScore;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.QuizQuestion;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.export.BundleExporter;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class StudyStoreTest extends TestContainerBase {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private StudyProgressStore study;

    @Autowired
    private QuizSessionStore sessions;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private BundleExporter exporter;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aRegenerationKeepsUnchangedCardsHistoryRetiresChangedOnesAndARevertRevivesThemDueNow() {
        UUID kbId = insertKnowledgeBase();
        UUID pageId = UUID.randomUUID();
        Flashcard kept = makeCard(kbId, pageId, "Co to mitoza?", "Podział.", "faza");
        Flashcard changed = makeCard(kbId, pageId, "Ile faz?", "Cztery.", "faza");
        study.replaceCards(kbId, pageId, List.of(kept, changed), NOW);
        study.recordReview(kbId, new CardState(kept, 2.6, 6, 2, NOW.plus(Duration.ofDays(6)), null), 5, NOW);
        study.recordReview(kbId, new CardState(changed, 2.6, 6, 2, NOW.plus(Duration.ofDays(6)), null), 5, NOW);

        Flashcard corrected = makeCard(kbId, pageId, "Ile faz?", "Pięć.", "etapy");
        Flashcard moved = new Flashcard(kept.cardId(), pageId, "przebieg", CardType.BASIC, kept.front(), kept.back(), "bbbbbbbbbbbbbbbb");
        study.replaceCards(kbId, pageId, List.of(moved, corrected), NOW.plusSeconds(10));

        assertThat(study.findCard(kbId, kept.cardId())).get().satisfies(card -> {
            assertThat(card.repetitions()).isEqualTo(2);
            assertThat(card.card().sectionAnchor()).isEqualTo("przebieg");
            assertThat(card.card().sourceHash()).isEqualTo("bbbbbbbbbbbbbbbb");
        });
        assertThat(study.findCard(kbId, changed.cardId())).get().extracting(CardState::retired).isEqualTo(true);

        Instant revertedAt = NOW.plusSeconds(20);
        study.replaceCards(kbId, pageId, List.of(kept, changed), revertedAt);

        assertThat(study.findCard(kbId, changed.cardId())).get().satisfies(card -> {
            assertThat(card.retired()).isFalse();
            assertThat(card.repetitions()).isEqualTo(2);
            assertThat(card.dueAt()).isEqualTo(revertedAt);
        });
        assertThat(study.findCard(kbId, corrected.cardId())).get().extracting(CardState::retired).isEqualTo(true);
        study.replaceCards(kbId, pageId, List.of(kept), revertedAt);
        assertThat(study.cardsOf(kbId, List.of(pageId))).hasSize(3);
    }

    @Test
    void dueCardsSkipDeletedPagesAndSupersededSectionsAndHealWhenTheSupersessionGoes() {
        UUID kbId = insertKnowledgeBase();
        UUID live = UUID.randomUUID();
        UUID superseding = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID supersessionId = UUID.randomUUID();
        completedRun(kbId, runId -> {
            wiki.savePage(kbId, makeWrite(live, "concepts/mitoza", "# Faza\n\nA.\n\n# Stare\n\nB.\n"), runId);
            wiki.savePage(kbId, makeWrite(superseding, "concepts/mejoza", "# Nowe\n\nC.\n"), runId);
            wiki.addSupersessions(kbId, List.of(new PageSupersession(supersessionId, live, "stare", superseding, runId,
                NOW)));
        });
        completedRun(kbId, runId -> wiki.savePage(kbId, makeWrite(deleted, "concepts/gamety", "# X\n\nD.\n"), runId));
        completedRun(kbId, runId -> wiki.deletePage(kbId, deleted, runId));
        Flashcard current = makeCard(kbId, live, "A?", "A.", "faza");
        Flashcard superseded = makeCard(kbId, live, "B?", "B.", "stare");
        study.replaceCards(kbId, live, List.of(current, superseded), NOW);
        study.replaceCards(kbId, deleted, List.of(makeCard(kbId, deleted, "D?", "D.", "x")), NOW);

        assertThat(study.dueCards(kbId, null, NOW)).extracting(card -> card.card().cardId())
            .containsExactly(current.cardId());
        assertThat(study.dueCards(kbId, List.of(live), NOW.minusSeconds(1))).isEmpty();

        jdbc.update("DELETE FROM page_supersessions WHERE supersession_id = ?", supersessionId);
        assertThat(study.dueCards(kbId, List.of(live), NOW)).hasSize(2);
    }

    @Test
    void aPageScoreIsTheMeanOfItsLastFiveEvents() {
        UUID kbId = insertKnowledgeBase();
        UUID pageId = UUID.randomUUID();
        int[] scores = {0, 0, 5, 5, 5, 5, 5};
        for (int i = 0; i < scores.length; i++) {
            study.recordQuizScore(kbId, pageId, scores[i], NOW.plusSeconds(i));
        }

        assertThat(study.pageScores(kbId)).containsExactly(new PageScore(pageId, 5.0));
    }

    @Test
    void studyDataNeverReachesAnExportAndGoesWithItsKnowledgeBase() {
        UUID kbId = insertKnowledgeBase();
        UUID pageId = UUID.randomUUID();
        completedRun(kbId, runId -> wiki.savePage(kbId, makeWrite(pageId, "concepts/mitoza", "# Faza\n\nA.\n"), runId));
        study.replaceCards(kbId, pageId, List.of(makeCard(kbId, pageId, "SEKRET-PRZOD", "SEKRET-TYL", "faza")), NOW);
        study.recordQuizScore(kbId, pageId, 1, NOW);
        UUID owner = jdbc.queryForObject("SELECT owner_id FROM knowledge_bases WHERE kb_id = ?", UUID.class, kbId);
        QuizSession session = new QuizSession(UUID.randomUUID(), kbId, owner, List.of(new QuizQuestion(pageId, "faza",
            "Pytanie?", "SEKRET-ODPOWIEDZ", "SEKRET-FRAGMENT")), 0, NOW.plus(Duration.ofHours(1)));
        sessions.insert(kbId, session);

        assertThat(sessions.find(kbId, session.sessionId(), NOW)).contains(session);
        assertThat(exporter.export(kbId, "Biologia").files().values()).noneMatch(file -> file.contains("SEKRET"));

        jdbc.update("DELETE FROM users WHERE user_id = ?", owner);
        for (String table : List.of("flashcards", "study_events", "quiz_sessions")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE knowledge_base_id = ?",
                Integer.class, kbId)).as(table).isZero();
        }
    }

    @Test
    void anExpiredSessionIsNeitherFoundNorKept() {
        UUID kbId = insertKnowledgeBase();
        UUID owner = jdbc.queryForObject("SELECT owner_id FROM knowledge_bases WHERE kb_id = ?", UUID.class, kbId);
        QuizSession expired = new QuizSession(UUID.randomUUID(), kbId, owner, List.of(), 0, NOW.minusSeconds(1));
        sessions.insert(kbId, expired);

        assertThat(sessions.find(kbId, expired.sessionId(), NOW)).isEmpty();
        assertThat(sessions.deleteExpired(NOW)).isGreaterThanOrEqualTo(1);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static Flashcard makeCard(UUID kbId, UUID pageId, String front, String back, String anchor) {
        return new Flashcard(Flashcard.computeCardId(kbId, pageId, CardType.BASIC, front, back), pageId, anchor,
            CardType.BASIC, front, back, "aaaaaaaaaaaaaaaa");
    }

    private static PageWrite makeWrite(UUID pageId, String path, String body) {
        return new PageWrite(pageId, path, "Tytuł", "Opis.", PageType.CONCEPT, body);
    }

    private void completedRun(UUID kbId, Consumer<UUID> writes) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
            + " source_filename, mime_type, upload_source, uploaded_by) SELECT ?, kb_id, 'lekcja', 'Lekcja', ?,"
            + " 'lekcja.md', 'text/markdown', 'API', owner_id FROM knowledge_bases WHERE kb_id = ?",
            documentId, documentId.toString(), kbId);
        UUID runId = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null)).runId();
        assertThat(runs.claim(kbId, runId)).isTrue();
        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, runId, List.of(), Map.of());
            writes.accept(runId);
        });
        runs.complete(kbId, runId, 0, false, List.of(), Map.of());
    }

    private UUID insertKnowledgeBase() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "study-" + userId + "@example.com");
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, userId);
        return kbId;
    }
}
