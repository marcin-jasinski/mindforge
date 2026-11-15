package dev.mindforge.slack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.webhook.WebhookResponse;

/**
 * The only class that answers a slash command. Every message is ephemeral (ADR 0032): a text sent before the ack
 * becomes the ack body, and every later text goes to the command's {@code response_url}. A text over the message
 * limit goes out as ordered parts.
 */
public class BoltSlackReply implements SlackReply {

    private static final Logger log = LoggerFactory.getLogger(BoltSlackReply.class);

    static final int MAX_MESSAGE_LENGTH = 4000;
    private static final String EPHEMERAL = "ephemeral";
    private static final int HTTP_OK = 200;

    /** Posts one message to the command's {@code response_url}. */
    @FunctionalInterface
    public interface Responder {
        WebhookResponse respond(SlashCommandResponse message) throws IOException;
    }

    private final Responder responder;
    private boolean acknowledged;
    private SlashCommandResponse ackBody;

    public BoltSlackReply(Responder responder) {
        this.responder = responder;
    }

    @Override
    public void ack() {
        acknowledged = true;
    }

    @Override
    public void send(String text) {
        parts(text).forEach(this::sendPart);
    }

    /** The text in ordered parts of at most the message limit, never splitting a surrogate pair. */
    static List<String> parts(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        do {
            int end = Math.min(text.length(), start + MAX_MESSAGE_LENGTH);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            parts.add(text.substring(start, end));
            start = end;
        } while (start < text.length());
        return parts;
    }

    /** The body to acknowledge the command with; empty when no text was sent before the ack. */
    public Optional<SlashCommandResponse> ackBody() {
        return Optional.ofNullable(ackBody);
    }

    private void sendPart(String part) {
        SlashCommandResponse message = SlashCommandResponse.builder().responseType(EPHEMERAL).text(part).build();
        if (!acknowledged) {
            ackBody = message;
            acknowledged = true;
            return;
        }
        try {
            WebhookResponse response = responder.respond(message);
            if (response.getCode() == null || response.getCode() != HTTP_OK) {
                log.warn("Slack reply rejected: status={}", response.getCode());
            }
        } catch (IOException e) {
            log.warn("Slack reply failed: error={}", e.getClass().getName());
        }
    }
}
