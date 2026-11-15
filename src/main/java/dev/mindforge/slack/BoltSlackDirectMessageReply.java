package dev.mindforge.slack;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

/**
 * The only class that answers a direct message to the bot. A direct message is private to the caller and the bot, so
 * a {@code chat.postMessage} into it is the private reply (ADR 0032, ADR 0035). A text over the message limit goes out
 * as ordered parts.
 */
public class BoltSlackDirectMessageReply implements SlackReply {

    private static final Logger log = LoggerFactory.getLogger(BoltSlackDirectMessageReply.class);

    /** Posts one message into the direct message the event came from. */
    @FunctionalInterface
    public interface Poster {
        ChatPostMessageResponse post(String text) throws IOException, SlackApiException;
    }

    private final Poster poster;

    public BoltSlackDirectMessageReply(Poster poster) {
        this.poster = poster;
    }

    /** Nothing to do: the listener acknowledges the event before any work starts. */
    @Override
    public void ack() {
    }

    @Override
    public void send(String text) {
        BoltSlackReply.parts(text).forEach(this::sendPart);
    }

    private void sendPart(String part) {
        try {
            ChatPostMessageResponse response = poster.post(part);
            if (!response.isOk()) {
                log.warn("Slack direct message reply rejected: error={}", response.getError());
            }
        } catch (IOException | SlackApiException e) {
            log.warn("Slack direct message reply failed: error={}", e.getClass().getName());
        }
    }
}
