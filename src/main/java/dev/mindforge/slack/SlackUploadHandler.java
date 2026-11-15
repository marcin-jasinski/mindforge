package dev.mindforge.slack;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.ExternalAccounts;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;

/**
 * A file sent to the bot in a direct message (ADR 0035): uploads it into the caller's only knowledge base through the
 * same {@link IngestionService#ingest} path as the web and the CLI. Only a user's {@code file_share} message in an
 * {@code im} channel is handled; every other message is ignored without a reply. The event is acknowledged first; the
 * gate runs on the executor in this order: team allowlist (ADR 0034), rate limit, identity, knowledge base, download.
 * One reply goes to the same direct message: queued, or a fixed refusal that never carries an exception's message.
 */
public class SlackUploadHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackUploadHandler.class);

    static final String IM = "im";
    static final String FILE_SHARE = "file_share";
    static final String ONE_FILE = "Send one file per message.";
    static final String LESSON_EXISTS = "Lesson exists; send new_version or another lesson_id.";
    static final String UNKNOWN_LESSON = "No lesson has this lesson_id; send it without new_version.";
    static final String LESSON_IDENTITY = "No valid lesson id; send a valid lesson_id.";
    static final String REJECTED = "The file was refused; check its type, name and size.";
    static final String TOO_LARGE = "The file is larger than the upload limit.";
    static final String NOT_FROM_SLACK = "The file is not from Slack's file host and was refused.";
    static final String DOWNLOAD_FAILED = "The file could not be downloaded. Try again later.";
    static final String BUSY = "The knowledge base is busy. Try again later.";

    private final SlackAllowlist allowlist;
    private final SlackRateLimiter rateLimiter;
    private final ExternalIdentityService identities;
    private final SlackKnowledgeBases knowledgeBases;
    private final SlackFiles files;
    private final IngestionService ingestion;
    private final Executor executor;

    public SlackUploadHandler(SlackAllowlist allowlist, SlackRateLimiter rateLimiter,
                              ExternalIdentityService identities, SlackKnowledgeBases knowledgeBases, SlackFiles files,
                              IngestionService ingestion, Executor executor) {
        this.allowlist = allowlist;
        this.rateLimiter = rateLimiter;
        this.identities = identities;
        this.knowledgeBases = knowledgeBases;
        this.files = files;
        this.ingestion = ingestion;
        this.executor = executor;
    }

    /** Returns at once; an upload runs later on the executor. */
    public void handle(SlackMessage message, SlackReply reply) {
        if (!IM.equals(message.channelType()) || !FILE_SHARE.equals(message.subtype())
            || message.botIdOrNull() != null) {
            return;
        }
        executor.execute(() -> run(message, reply));
    }

    private void run(SlackMessage message, SlackReply reply) {
        try {
            String text = upload(message, reply);
            if (text != null) {
                reply.send(text);
            }
        } catch (RuntimeException e) {
            log.warn("Slack upload failed: error={}", e.getClass().getName());
            reply.send(SlackCommandHandler.FAILED);
        }
    }

    /** The reply text, or null when the knowledge base resolver already replied. */
    private String upload(SlackMessage message, SlackReply reply) {
        if (!allowlist.admits(message.workspaceTeamId(), message.callerTeamId())) {
            log.info("Slack upload rejected: reason=team_not_allowed");
            return SlackCommandHandler.NOT_AVAILABLE;
        }
        OptionalLong retryAfter = rateLimiter.tryAcquire(message.slackUserId());
        if (retryAfter.isPresent()) {
            return "Rate limit reached. Try again in " + retryAfter.getAsLong() + " s.";
        }
        if (message.files().size() != 1) {
            return ONE_FILE;
        }
        Optional<SlackUploadText.UploadOptions> options = SlackUploadText.parse(message.text());
        if (options.isEmpty()) {
            return SlackUploadText.GRAMMAR;
        }
        UUID userId = identities.resolve(ExternalPlatform.SLACK,
            ExternalAccounts.slackExternalId(message.callerTeamId(), message.slackUserId()), null);
        Optional<UUID> kbId = knowledgeBases.resolveKnowledgeBase(userId, reply);
        if (kbId.isEmpty()) {
            return null;
        }
        return ingest(kbId.get(), userId, message.files().getFirst(), options.get());
    }

    private String ingest(UUID kbId, UUID userId, SlackMessage.File file, SlackUploadText.UploadOptions options) {
        try {
            byte[] content = files.read(file.urlPrivateDownload(), file.size());
            UUID documentId = ingestion.ingest(new DocumentUpload(kbId, userId, UploadSource.SLACK, file.name(),
                mediaType(file.mimeType()), content, options.lessonId(), options.newVersion()));
            return "Document " + documentId + " accepted; its ingest run is queued. An identical file already in "
                + "this knowledge base returns that document and queues no run.";
        } catch (SlackFileRejectedException e) {
            return switch (e.reason()) {
                case NOT_FROM_SLACK -> NOT_FROM_SLACK;
                case TOO_LARGE -> TOO_LARGE;
                case DOWNLOAD_FAILED -> DOWNLOAD_FAILED;
            };
        } catch (LessonAlreadyExistsException e) {
            return LESSON_EXISTS;
        } catch (UnknownLessonException e) {
            return UNKNOWN_LESSON;
        } catch (LessonIdentityException e) {
            return LESSON_IDENTITY;
        } catch (UploadRejectedException e) {
            return REJECTED;
        } catch (KnowledgeBaseBusyException e) {
            return BUSY;
        }
    }

    /** The upload allowlist and the parsers match the bare media type; a missing one stays null and is refused. */
    private static String mediaType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String type = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return type.strip().toLowerCase(Locale.ROOT);
    }
}
