package dev.mindforge.integration.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

import dev.mindforge.api.controller.RunController;
import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.cli.CliExitOnReady;
import dev.mindforge.cli.CliUsageRunner;
import dev.mindforge.cli.ConsoleProgressNotifier;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.infrastructure.event.RunWorkerTriggers;
import dev.mindforge.infrastructure.event.SseProgressNotifier;
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
class CliProfileContextTest extends TestContainerBase {

    private static final long SWEEP_INTERVAL_MILLIS = 200;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void shortSweepInterval(DynamicPropertyRegistry registry) {
        registry.add("mindforge.runs.sweep-interval", () -> "PT0.2S");
    }

    @Test
    void theCliProfileTurnsTheWebApplicationTypeOff() {
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
    }

    @Test
    void theCliContextHasTheApplicationServicesButNoWebStack() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBeanNamesForType(IngestionService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(QuizService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(KnowledgeBaseService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(AccountService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(RunWorker.class)).hasSize(1);
    }

    @Test
    void theCliContextHasNoRunWorkerTriggers() {
        assertThat(context.getBeanNamesForType(RunWorkerTriggers.class)).isEmpty();
    }

    @Test
    void theOnlyProgressNotifierIsTheConsoleNotifier() {
        assertThat(context.getBeansOfType(ProgressNotifier.class).values())
            .singleElement().isInstanceOf(ConsoleProgressNotifier.class);
        assertThat(context.getBeanNamesForType(SseProgressNotifier.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RunController.class)).isEmpty();
    }

    @Test
    void withoutACommandTheContextLoadsReportsTheRefusedExitCodeAndDoesNotExit() {
        assertThat(context.getBean(CliUsageRunner.class).getExitCode()).isEqualTo(2);
        assertThat(context.getBeanNamesForType(CliExitOnReady.class)).isEmpty();
    }

    @Test
    void aRunningRunOfAnotherProcessIsNotSweptByTheCliContext() throws InterruptedException {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID documentId = insertDocument(kbId, userId);
        IngestRun run = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, documentId, null));
        assertThat(runs.claim(kbId, run.runId())).isTrue();

        Thread.sleep(SWEEP_INTERVAL_MILLIS * 10);

        assertThat(runs.findById(kbId, run.runId())).map(IngestRun::status).contains(RunStatus.RUNNING);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "cli-" + userId + "@example.com");
        return userId;
    }

    private UUID insertKnowledgeBase(UUID ownerId) {
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, ownerId);
        return kbId;
    }

    private UUID insertDocument(UUID kbId, UUID userId) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
                + " source_filename, mime_type, upload_source, uploaded_by)"
                + " VALUES (?, ?, 'cli-lesson', 'CLI Lesson', ?, 'notes.md', 'text/markdown', 'API', ?)",
            documentId, kbId, documentId.toString(), userId);
        return documentId;
    }
}
