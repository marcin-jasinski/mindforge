package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import dev.mindforge.slack.BoltSlackDirectMessageReply;

class BoltSlackDirectMessageReplyTest {

    private static final int LIMIT = 4000;

    private final List<String> posted = new ArrayList<>();

    // AC7

    @Test
    void aText_isPostedIntoTheDirectMessageInOrderedPartsUnderTheLimit() {
        BoltSlackDirectMessageReply reply = new BoltSlackDirectMessageReply(text -> {
            posted.add(text);
            ChatPostMessageResponse response = new ChatPostMessageResponse();
            response.setOk(true);
            return response;
        });

        reply.ack();
        reply.send("a".repeat(LIMIT) + "b");

        assertThat(posted).containsExactly("a".repeat(LIMIT), "b");
    }

    @Test
    void aFailedPost_doesNotThrow() {
        BoltSlackDirectMessageReply reply = new BoltSlackDirectMessageReply(text -> {
            throw new IOException("down");
        });

        assertThatCode(() -> reply.send("queued")).doesNotThrowAnyException();
    }
}
