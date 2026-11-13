package dev.mindforge.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.User;
import dev.mindforge.infrastructure.parsing.DocxParser;
import dev.mindforge.infrastructure.parsing.MarkdownParser;
import dev.mindforge.infrastructure.parsing.PdfParser;
import dev.mindforge.infrastructure.parsing.PlainTextParser;

/**
 * The {@code pipeline} command: admits a file into a knowledge base as a named user, through the same
 * {@link IngestionService#ingest} as an upload. Every refusal before a run is queued reports {@link CliExit#REFUSED}
 * with one line on stderr.
 */
public class PipelineCommandRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(PipelineCommandRunner.class);

    private static final String OCTET_STREAM = "application/octet-stream";
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
        "md", MarkdownParser.MIME_TYPE,
        "markdown", MarkdownParser.MIME_TYPE,
        "txt", PlainTextParser.MIME_TYPE,
        "pdf", PdfParser.MIME_TYPE,
        "docx", DocxParser.MIME_TYPE);

    private final AccountService accounts;
    private final KnowledgeBaseService knowledgeBases;
    private final IngestionService ingestion;
    private final PrintStream out;
    private final PrintStream err;
    private volatile CliExit exit = CliExit.OK;

    public PipelineCommandRunner(AccountService accounts, KnowledgeBaseService knowledgeBases,
                                 IngestionService ingestion, PrintStream out, PrintStream err) {
        this.accounts = accounts;
        this.knowledgeBases = knowledgeBases;
        this.ingestion = ingestion;
        this.out = out;
        this.err = err;
    }

    /** The MIME type named by the file's extension, case-insensitive; any other extension is octet-stream. */
    public static String mimeTypeOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return OCTET_STREAM;
        }
        return MIME_BY_EXTENSION.getOrDefault(name.substring(dot + 1).toLowerCase(Locale.ROOT), OCTET_STREAM);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!CliCommand.of(args).filter(CliCommand.PIPELINE::equals).isPresent()) {
            return;
        }
        String file = option(args, "file");
        String kb = option(args, "kb");
        String email = option(args, "user");
        UUID kbId = kb == null ? null : parseUuid(kb);
        if (file == null || email == null || kbId == null) {
            err.print(CliUsageRunner.USAGE);
            exit = CliExit.REFUSED;
            return;
        }
        try {
            exit = ingest(file, kbId, email);
        } catch (RuntimeException e) {
            log.error("The pipeline command failed", e);
            err.println("The pipeline command failed: " + e.getMessage());
            exit = CliExit.RUN_FAILED;
        }
    }

    @Override
    public int getExitCode() {
        return exit.code();
    }

    private CliExit ingest(String fileOption, UUID kbId, String email) {
        User user;
        try {
            user = accounts.byEmail(email);
        } catch (NotFoundException e) {
            return refuse("user not found: " + email);
        }
        try {
            knowledgeBases.requireOwner(kbId, user.userId());
        } catch (NotFoundException | NotOwnerException e) {
            return refuse("knowledge base not found or not owned by " + email);
        }
        Path file;
        byte[] content;
        try {
            file = Path.of(fileOption);
            content = Files.readAllBytes(file);
        } catch (IOException | InvalidPathException e) {
            return refuse("cannot read file: " + fileOption);
        }
        try {
            UUID documentId = ingestion.ingest(new DocumentUpload(kbId, user.userId(), UploadSource.CLI,
                file.getFileName().toString(), mimeTypeOf(file), content, null, false));
            out.println("Document " + documentId);
            return CliExit.OK;
        } catch (UploadRejectedException | LessonIdentityException | UnknownLessonException
                 | LessonAlreadyExistsException e) {
            return refuse(e.getMessage());
        }
    }

    private CliExit refuse(String message) {
        err.println(message);
        return CliExit.REFUSED;
    }

    private static String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            return null;
        }
        return values.getFirst();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
