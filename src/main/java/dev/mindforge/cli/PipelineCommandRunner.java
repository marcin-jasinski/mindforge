package dev.mindforge.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.infrastructure.parsing.DocxParser;
import dev.mindforge.infrastructure.parsing.MarkdownParser;
import dev.mindforge.infrastructure.parsing.PdfParser;
import dev.mindforge.infrastructure.parsing.PlainTextParser;

/**
 * The {@code pipeline} command: admits a file into a knowledge base as a named user, through the same
 * {@link IngestionService#ingest} as an upload, then drains the knowledge base's queue in this process and waits for
 * its own run. Every refusal before a run is queued reports {@link CliExit#REFUSED} with one line on stderr; a run
 * that failed (now or before) or did not end in time reports {@link CliExit#RUN_FAILED}.
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
    private final IngestRunRepository runs;
    private final RunWorker runWorker;
    private final ConsoleProgressNotifier progress;
    private final Duration runTimeout;
    private final Duration pollInterval;
    private final PrintStream out;
    private final PrintStream err;
    private volatile CliExit exit = CliExit.OK;

    public PipelineCommandRunner(AccountService accounts, KnowledgeBaseService knowledgeBases,
                                 IngestionService ingestion, IngestRunRepository runs, RunWorker runWorker,
                                 ConsoleProgressNotifier progress, Duration runTimeout, Duration pollInterval,
                                 PrintStream out, PrintStream err) {
        this.accounts = accounts;
        this.knowledgeBases = knowledgeBases;
        this.ingestion = ingestion;
        this.runs = runs;
        this.runWorker = runWorker;
        this.progress = progress;
        this.runTimeout = runTimeout;
        this.pollInterval = pollInterval;
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
            exit = ingest(file, kbId, email, option(args, "lesson-id"), args.containsOption("new-version"));
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

    private CliExit ingest(String fileOption, UUID kbId, String email, String lessonId, boolean newVersion) {
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
        UUID documentId;
        try {
            documentId = ingestion.ingest(new DocumentUpload(kbId, user.userId(), UploadSource.CLI,
                file.getFileName().toString(), mimeTypeOf(file), content, lessonId, newVersion));
        } catch (LessonAlreadyExistsException e) {
            return refuse("Lesson '" + e.lesson().lessonId() + "' already exists; pass --new-version to upload a new"
                + " version, or choose another --lesson-id");
        } catch (UploadRejectedException | LessonIdentityException | UnknownLessonException e) {
            return refuse(e.getMessage());
        }
        out.println("Document " + documentId);
        return report(kbId, documentId);
    }

    /**
     * A QUEUED latest run is the one this call queued: drain the queue and wait for it. Any other latest run means
     * the upload was identical to a stored document and no run was queued: report that run's state, do not wait.
     */
    private CliExit report(UUID kbId, UUID documentId) {
        Optional<IngestRun> latest = runs.latestForDocument(kbId, documentId);
        if (latest.isEmpty()) {
            out.println("Already ingested; no run");
            return CliExit.OK;
        }
        IngestRun run = latest.get();
        return switch (run.status()) {
            case QUEUED -> drainAndAwait(kbId, run.runId());
            case FAILED -> {
                err.println("Already ingested; run " + run.runId() + " FAILED: " + run.failureReason()
                    + ". Retry or remove the document in the web UI");
                yield CliExit.RUN_FAILED;
            }
            case RUNNING, WRITTEN, COMPLETED -> {
                out.println("Already ingested; run " + run.runId() + " " + run.status());
                yield CliExit.OK;
            }
        };
    }

    private CliExit drainAndAwait(UUID kbId, UUID runId) {
        progress.watch(runId);
        try {
            runWorker.drain(kbId);
            return outcome(await(kbId, runId), runId);
        } finally {
            runWorker.stop();
        }
    }

    /** Polls the run until it is COMPLETED or FAILED; empty when the run timeout passes first. */
    private Optional<IngestRun> await(UUID kbId, UUID runId) {
        long deadline = System.nanoTime() + runTimeout.toNanos();
        while (true) {
            Optional<IngestRun> run = runs.findById(kbId, runId)
                .filter(r -> r.status() == RunStatus.COMPLETED || r.status() == RunStatus.FAILED);
            if (run.isPresent() || System.nanoTime() - deadline >= 0) {
                return run;
            }
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    private CliExit outcome(Optional<IngestRun> ended, UUID runId) {
        if (ended.isEmpty()) {
            err.println("Run " + runId + " did not end within " + runTimeout);
            return CliExit.RUN_FAILED;
        }
        IngestRun run = ended.get();
        if (run.status() == RunStatus.FAILED) {
            err.println("Run " + runId + " FAILED: " + run.failureReason());
            return CliExit.RUN_FAILED;
        }
        if (!run.failures().isEmpty() || run.supersessionSkipped()) {
            err.println("Warning: run " + runId + " COMPLETED with " + run.failures().size() + " run failure(s)"
                + (run.supersessionSkipped() ? " and supersession skipped" : ""));
        }
        out.println("Run " + runId + " COMPLETED");
        return CliExit.OK;
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
