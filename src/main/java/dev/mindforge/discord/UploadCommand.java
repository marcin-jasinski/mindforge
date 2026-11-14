package dev.mindforge.discord;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.domain.model.UploadSource;

/**
 * {@code /upload}: uploads an attachment into a knowledge base through the same {@link IngestionService#ingest} path
 * as the web and the CLI. The attachment comes only from Discord's CDN (ADR 0029). The reply says the run is queued
 * and does not wait for it; a refusal gets a fixed text, never the exception's message.
 */
public class UploadCommand implements DiscordCommand {

    private static final Logger log = LoggerFactory.getLogger(UploadCommand.class);

    static final String NAME = "upload";
    static final String FILE = "file";
    static final String LESSON_ID = "lesson_id";
    static final String NEW_VERSION = "new_version";
    static final String LESSON_EXISTS = "Lesson exists; send new_version or another lesson_id.";
    static final String UNKNOWN_LESSON = "No lesson has this lesson_id; send it without new_version.";
    static final String LESSON_IDENTITY = "No valid lesson id; send a valid lesson_id.";
    static final String REJECTED = "The file was refused; check its type, name and size.";
    static final String TOO_LARGE = "The file is larger than the upload limit.";
    static final String NOT_FROM_DISCORD = "The attachment is not from Discord's CDN and was refused.";
    static final String DOWNLOAD_FAILED = "The attachment could not be downloaded. Try again later.";
    static final String BUSY = "The knowledge base is busy. Try again later.";

    private final IngestionService ingestion;
    private final DiscordKnowledgeBases knowledgeBases;
    private final DiscordAttachments attachments;
    private final long maxBytes;

    public UploadCommand(IngestionService ingestion, DiscordKnowledgeBases knowledgeBases,
                         DiscordAttachments attachments, long maxBytes) {
        this.ingestion = ingestion;
        this.knowledgeBases = knowledgeBases;
        this.attachments = attachments;
        this.maxBytes = maxBytes;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash(NAME, "Upload a document into one of your knowledge bases")
            .addOptions(
                DiscordKnowledgeBases.option(),
                new OptionData(OptionType.ATTACHMENT, FILE, "The document", true),
                new OptionData(OptionType.STRING, LESSON_ID, "Lesson id; overrides the one read from the file"),
                new OptionData(OptionType.BOOLEAN, NEW_VERSION, "Upload a new version of an existing lesson"));
    }

    @Override
    public void handle(DiscordCall call, UUID userId, DiscordReply reply) {
        Optional<UUID> kbId = knowledgeBases.resolveKnowledgeBase(call.options().get(DiscordKnowledgeBases.OPTION),
            userId, reply);
        if (kbId.isEmpty()) {
            return;
        }
        Message.Attachment attachment = call.attachments().get(FILE);
        if (attachment == null) {
            log.warn("Discord /upload without an attachment");
            reply.send(DiscordCommandHandler.FAILED);
            return;
        }
        reply.send(upload(call, kbId.get(), userId, attachment));
    }

    private String upload(DiscordCall call, UUID kbId, UUID userId, Message.Attachment attachment) {
        try {
            byte[] content = attachments.read(attachment, maxBytes);
            UUID documentId = ingestion.ingest(new DocumentUpload(kbId, userId, UploadSource.DISCORD,
                attachment.getFileName(), mediaType(attachment.getContentType()), content, call.options().get(LESSON_ID),
                Boolean.parseBoolean(call.options().get(NEW_VERSION))));
            return "Document " + documentId + " accepted; its ingest run is queued. An identical file already in "
                + "this knowledge base returns that document and queues no run.";
        } catch (AttachmentRejectedException e) {
            return switch (e.reason()) {
                case NOT_FROM_DISCORD -> NOT_FROM_DISCORD;
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

    /**
     * Discord sends text attachments with parameters ({@code text/markdown; charset=utf-8}); the upload allowlist and
     * the parsers match the bare media type. A missing content type stays null and is refused.
     */
    static String mediaType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String type = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return type.strip().toLowerCase(Locale.ROOT);
    }
}
