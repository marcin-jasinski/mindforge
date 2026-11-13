package dev.mindforge.integration.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.cli.ConsoleProgressNotifier;
import dev.mindforge.cli.PipelineCommandRunner;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.IngestRunRepository;
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
    "mindforge.cli.exit-when-done=false",
    "mindforge.cli.run-timeout=PT30S"
})
class PipelineCommandIntegrationTest extends TestContainerBase {

    private static final String NOTES = "# Mitosis\n\nMitosis is cell division.\n\n# Meiosis\n\nMeiosis makes gametes.\n";

    /** The console the run progress is printed to; the context's runner prints to this one console. */
    private static final ByteArrayOutputStream STDOUT = new ByteArrayOutputStream();

    @TestConfiguration
    static class CapturedConsole {

        @Bean
        @Primary
        ConsoleProgressNotifier capturedProgressNotifier() {
            return new ConsoleProgressNotifier(new PrintStream(STDOUT, true, StandardCharsets.UTF_8));
        }
    }

    @Autowired
    private PipelineCommandRunner runner;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private StubAIGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    private Path dir;

    @Test
    void thePipelineCommandIngestsAMarkdownFileToCompletedAndPrintsItsProgress() throws IOException {
        String email = "cli-" + UUID.randomUUID() + "@example.com";
        UUID kbId = insertKnowledgeBase(insertUser(email));
        givenTheNotesAnswers();
        Path file = Files.writeString(dir.resolve("biology.md"), NOTES);

        runner.run(new DefaultApplicationArguments("pipeline", "--file=" + file, "--kb=" + kbId, "--user=" + email));

        assertThat(runner.getExitCode()).isZero();
        UUID documentId = jdbc.queryForObject("SELECT document_id FROM documents WHERE knowledge_base_id = ?",
            UUID.class, kbId);
        assertThat(documents.findById(kbId, documentId)).map(Document::uploadSource).contains(UploadSource.CLI);
        IngestRun run = runs.latestForDocument(kbId, documentId).orElseThrow();
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(STDOUT.toString(StandardCharsets.UTF_8).lines())
            .anyMatch(line -> line.startsWith("[" + run.runId().toString().substring(0, 8) + "]"));
    }

    private void givenTheNotesAnswers() {
        gateway.reset();
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, Prompts.json(Map.of(
            "claims", List.of(claim("Mitosis is cell division.", "Mitosis", 0, 1),
                claim("Meiosis makes gametes.", "Meiosis", 2, 3)),
            "chunkDigest", "Mitosis and meiosis.")));
        gateway.answer(Prompts.writing("concepts/mitosis"), draft("Cell division.", "# Course\n\nMitosis.\n"));
        gateway.answer(Prompts.writing("concepts/meiosis"), draft("Reductive division.", "# Gametes\n\nMeiosis.\n"));
        gateway.answer(Prompts.writing("sources/biology"), draft("Biology notes.", "# Summary\n\nBoth.\n"));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    private static Map<String, Object> claim(String text, String title, int firstBlock, int lastBlock) {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("text", text);
        claim.put("title", title);
        claim.put("targetPath", null);
        claim.put("firstBlock", firstBlock);
        claim.put("lastBlock", lastBlock);
        return claim;
    }

    private static String draft(String description, String body) {
        return Prompts.json(Map.of("description", description, "body", body));
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
