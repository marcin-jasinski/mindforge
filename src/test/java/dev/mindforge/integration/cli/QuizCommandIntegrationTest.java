package dev.mindforge.integration.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.cli.QuizCommandRunner;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.PageScore;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("cli")
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none",
    "mindforge.cli.exit-when-done=false"
})
class QuizCommandIntegrationTest extends TestContainerBase {

    private static final String REFERENCE_ANSWER = "SECRET-REFERENCE-ANSWER";
    private static final String GROUNDING_EXCERPT = "SECRET-GROUNDING-EXCERPT";

    @Autowired
    private AccountService accounts;

    @Autowired
    private KnowledgeBaseService knowledgeBases;

    @Autowired
    private QuizService quizzes;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private StudyProgressStore study;

    @Autowired
    private StubAIGateway gateway;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theQuizCommandGradesAnAnswerPrintsASummaryAndRecordsTheScore() {
        String email = "quiz-" + UUID.randomUUID() + "@example.com";
        UUID kbId = insertKnowledgeBase(insertUser(email));
        UUID pageId = UUID.randomUUID();
        savePage(kbId, new PageWrite(pageId, "concepts/mitosis", "Mitosis", "Cell division.", PageType.CONCEPT,
            "# Course\n\nMitosis splits a cell in two.\n"));
        gateway.reset();
        gateway.answer(Prompts.QUIZ, Prompts.json(Map.of("questions", List.of(Map.of("pagePath", "concepts/mitosis",
            "question", "What does mitosis mean?", "referenceAnswer", REFERENCE_ANSWER,
            "groundingExcerpt", GROUNDING_EXCERPT)))));
        gateway.answer(Prompts.GRADE, "{\"score\": 4, \"feedback\": \"Almost right.\"}");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        QuizCommandRunner runner = new QuizCommandRunner(accounts, knowledgeBases, quizzes,
            new ByteArrayInputStream("Division.\n".getBytes(StandardCharsets.UTF_8)),
            new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments("quiz", "--kb=" + kbId, "--user=" + email));

        assertThat(runner.getExitCode()).isZero();
        String stdout = out.toString(StandardCharsets.UTF_8);
        assertThat(stdout).contains("Question 1/1: What does mitosis mean?", "Score: 4/5", "Almost right.",
            "Quiz ended: 1 of 1 questions answered, total score 4, mean score 4.0/5");
        for (String console : List.of(stdout, err.toString(StandardCharsets.UTF_8))) {
            assertThat(console).doesNotContain(REFERENCE_ANSWER).doesNotContain(GROUNDING_EXCERPT);
        }
        assertThat(study.pageScores(kbId)).containsExactly(new PageScore(pageId, 4.0));
    }

    private void savePage(UUID kbId, PageWrite page) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
            + " source_filename, mime_type, upload_source, uploaded_by) SELECT ?, kb_id, 'lesson', 'Lesson', ?,"
            + " 'lesson.md', 'text/markdown', 'API', owner_id FROM knowledge_bases WHERE kb_id = ?",
            documentId, documentId.toString(), kbId);
        UUID runId = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null)).runId();
        assertThat(runs.claim(kbId, runId)).isTrue();
        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, runId, List.of(), Map.of());
            wiki.savePage(kbId, page, runId);
        });
        runs.complete(kbId, runId, 0, false, List.of(), Map.of());
    }

    private UUID insertUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)", userId, email);
        return userId;
    }

    private UUID insertKnowledgeBase(UUID ownerId) {
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, ownerId);
        return kbId;
    }
}
