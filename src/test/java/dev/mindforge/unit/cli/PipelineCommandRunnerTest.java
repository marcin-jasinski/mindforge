package dev.mindforge.unit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.cli.PipelineCommandRunner;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.User;
import dev.mindforge.infrastructure.parsing.DocxParser;
import dev.mindforge.infrastructure.security.UploadSanitizer;

class PipelineCommandRunnerTest {

    private static final String EMAIL = "ada@example.com";
    private static final UUID KB_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final byte[] CONTENT = "# Notes\n\nBody.".getBytes(StandardCharsets.UTF_8);

    private final AccountService accounts = mock(AccountService.class);
    private final KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);
    private final IngestionService ingestion = mock(IngestionService.class);
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final PipelineCommandRunner runner = new PipelineCommandRunner(accounts, knowledgeBases, ingestion,
        new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));

    @TempDir
    private Path dir;

    @Test
    void anotherCommandDoesNothing() {
        runner.run(new DefaultApplicationArguments("quiz", "--kb=" + KB_ID, "--user=" + EMAIL));

        assertThat(runner.getExitCode()).isZero();
        assertThat(err.size()).isZero();
        verifyNoInteractions(accounts, knowledgeBases, ingestion);
    }

    @Test
    void aMissingOrInvalidOptionPrintsTheUsageAndCallsNoService() throws IOException {
        Path file = makeFile("notes.md");
        List<String[]> cases = List.of(
            new String[] {"pipeline", "--kb=" + KB_ID, "--user=" + EMAIL},
            new String[] {"pipeline", "--file=" + file, "--user=" + EMAIL},
            new String[] {"pipeline", "--file=" + file, "--kb=" + KB_ID},
            new String[] {"pipeline", "--file=" + file, "--kb=not-a-uuid", "--user=" + EMAIL},
            new String[] {"pipeline", "--file", file.toString(), "--kb=" + KB_ID, "--user=" + EMAIL});
        for (String[] args : cases) {
            ByteArrayOutputStream caseErr = new ByteArrayOutputStream();
            PipelineCommandRunner caseRunner = new PipelineCommandRunner(accounts, knowledgeBases, ingestion,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(caseErr, true, StandardCharsets.UTF_8));

            caseRunner.run(new DefaultApplicationArguments(args));

            assertThat(caseErr.toString(StandardCharsets.UTF_8)).as(String.join(" ", args)).startsWith("Usage:");
            assertThat(caseRunner.getExitCode()).as(String.join(" ", args)).isEqualTo(2);
        }
        verifyNoInteractions(accounts, knowledgeBases, ingestion);
    }

    @Test
    void anUnknownUserIsRefusedBeforeTheOwnershipCheck() throws IOException {
        when(accounts.byEmail(EMAIL)).thenThrow(new NotFoundException("User"));

        runner.run(makeArgs(makeFile("notes.md")));

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).containsExactly("user not found: " + EMAIL);
        verifyNoInteractions(knowledgeBases, ingestion);
    }

    @Test
    void aKnowledgeBaseOfAnotherOwnerIsRefusedWithoutIngest() throws IOException {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        doThrow(new NotOwnerException(KB_ID)).when(knowledgeBases).requireOwner(KB_ID, USER_ID);

        runner.run(makeArgs(makeFile("notes.md")));

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).containsExactly("knowledge base not found or not owned by " + EMAIL);
        verifyNoInteractions(ingestion);
    }

    @Test
    void anUnknownKnowledgeBaseIsRefusedWithoutIngest() throws IOException {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        doThrow(new NotFoundException("Knowledge base")).when(knowledgeBases).requireOwner(KB_ID, USER_ID);

        runner.run(makeArgs(makeFile("notes.md")));

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).containsExactly("knowledge base not found or not owned by " + EMAIL);
        verifyNoInteractions(ingestion);
    }

    @Test
    void aMixedCaseAndPaddedEmailIsResolvedByTheAccountService() throws IOException {
        String raw = "  Ada@Example.COM ";
        when(accounts.byEmail(raw)).thenReturn(makeUser());
        when(ingestion.ingest(any())).thenReturn(DOCUMENT_ID);

        runner.run(new DefaultApplicationArguments("pipeline", "--file=" + makeFile("notes.md"), "--kb=" + KB_ID,
            "--user=" + raw));

        assertThat(runner.getExitCode()).isZero();
        verify(accounts).byEmail(raw);
        verify(knowledgeBases).requireOwner(KB_ID, USER_ID);
    }

    @Test
    void aMissingFileIsRefusedWithoutIngest() {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        Path missing = dir.resolve("missing.md");

        runner.run(makeArgs(missing));

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).containsExactly("cannot read file: " + missing);
        verify(ingestion, never()).ingest(any());
    }

    @Test
    void theHappyPathIngestsTheFileAsTheResolvedUserFromTheCli() throws IOException {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        when(ingestion.ingest(any())).thenReturn(DOCUMENT_ID);
        Path file = makeFile("Notes.DOCX");

        runner.run(makeArgs(file));

        ArgumentCaptor<DocumentUpload> upload = ArgumentCaptor.forClass(DocumentUpload.class);
        verify(ingestion).ingest(upload.capture());
        assertThat(upload.getValue()).usingRecursiveComparison().isEqualTo(new DocumentUpload(KB_ID, USER_ID,
            UploadSource.CLI, "Notes.DOCX", DocxParser.MIME_TYPE, CONTENT, null, false));
        assertThat(runner.getExitCode()).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains(DOCUMENT_ID.toString());
        assertThat(err.size()).isZero();
    }

    @Test
    void eachRefusalOfTheIngestPrintsItsMessageAndReportsTheRefusedExitCode() throws IOException {
        List<RuntimeException> refusals = List.of(
            new UploadRejectedException("The upload is too large"),
            new LessonIdentityException("Invalid lesson id"),
            new UnknownLessonException("intro"),
            new LessonAlreadyExistsException(new LessonIdentity("intro", "Intro")));
        Path file = makeFile("notes.md");
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        for (RuntimeException refusal : refusals) {
            ByteArrayOutputStream caseErr = new ByteArrayOutputStream();
            PipelineCommandRunner caseRunner = new PipelineCommandRunner(accounts, knowledgeBases, ingestion,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(caseErr, true, StandardCharsets.UTF_8));
            doThrow(refusal).when(ingestion).ingest(any());

            caseRunner.run(makeArgs(file));

            assertThat(caseErr.toString(StandardCharsets.UTF_8).strip()).isEqualTo(refusal.getMessage());
            assertThat(caseRunner.getExitCode()).as(refusal.getClass().getSimpleName()).isEqualTo(2);
        }
    }

    @Test
    void anUnsupportedFileShowsTheSanitizersMessage() throws IOException {
        UploadSanitizer sanitizer = new UploadSanitizer(1_000_000, Set.of("text/markdown"));
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        when(ingestion.ingest(any())).thenAnswer(call -> {
            DocumentUpload upload = call.getArgument(0);
            sanitizer.admit(upload.filename(), upload.mimeType(), upload.content().length);
            return DOCUMENT_ID;
        });

        runner.run(makeArgs(makeFile("script.sh")));

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(stderrLines()).containsExactly("Unsupported document type: application/octet-stream");
    }

    @Test
    void anUnexpectedFailureReportsTheRunFailedExitCode() throws IOException {
        when(accounts.byEmail(EMAIL)).thenReturn(makeUser());
        when(ingestion.ingest(any())).thenThrow(new IllegalStateException("database down"));

        runner.run(makeArgs(makeFile("notes.md")));

        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    private DefaultApplicationArguments makeArgs(Path file) {
        return new DefaultApplicationArguments("--spring.profiles.active=cli", "pipeline", "--file=" + file,
            "--kb=" + KB_ID, "--user=" + EMAIL);
    }

    private Path makeFile(String name) throws IOException {
        return Files.write(dir.resolve(name), CONTENT);
    }

    private List<String> stderrLines() {
        return err.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private static User makeUser() {
        return new User(USER_ID, "Ada", EMAIL, null, null, null, null);
    }
}
