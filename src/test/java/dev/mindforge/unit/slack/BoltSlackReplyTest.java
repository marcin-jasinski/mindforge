package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.webhook.WebhookResponse;
import dev.mindforge.slack.BoltSlackReply;

class BoltSlackReplyTest {

    private static final int LIMIT = 4000;

    private final List<SlashCommandResponse> posted = new ArrayList<>();

    // AC9

    @Test
    void aTextBeforeTheAck_becomesTheEphemeralAckBody() {
        BoltSlackReply reply = makeReply();

        reply.send("This bot is not available here.");

        assertThat(reply.ackBody()).hasValueSatisfying(body -> {
            assertThat(body.getResponseType()).isEqualTo("ephemeral");
            assertThat(body.getText()).isEqualTo("This bot is not available here.");
        });
        assertThat(posted).isEmpty();
    }

    @Test
    void afterAnAck_textsGoToTheResponseUrlAsEphemeralMessages() {
        BoltSlackReply reply = makeReply();

        reply.ack();
        reply.send("answer");

        assertThat(reply.ackBody()).isEmpty();
        assertThat(posted).singleElement().satisfies(message -> {
            assertThat(message.getResponseType()).isEqualTo("ephemeral");
            assertThat(message.getText()).isEqualTo("answer");
        });
    }

    @Test
    void aLongText_goesOutAsOrderedEphemeralPartsUnderTheLimit() {
        BoltSlackReply reply = makeReply();
        String text = "a".repeat(LIMIT) + "b".repeat(LIMIT) + "c";

        reply.ack();
        reply.send(text);

        assertThat(posted).extracting(SlashCommandResponse::getText)
            .containsExactly("a".repeat(LIMIT), "b".repeat(LIMIT), "c");
        assertThat(posted).allSatisfy(message -> assertThat(message.getResponseType()).isEqualTo("ephemeral"));
    }

    @Test
    void aSplit_neverCutsASurrogatePair() {
        BoltSlackReply reply = makeReply();
        String emoji = "😀";
        String text = "a".repeat(LIMIT - 1) + emoji + "b";

        reply.ack();
        reply.send(text);

        assertThat(posted).extracting(SlashCommandResponse::getText)
            .containsExactly("a".repeat(LIMIT - 1), emoji + "b");
    }

    @Test
    void aLongTextBeforeTheAck_putsTheFirstPartInTheAckAndTheRestAtTheResponseUrl() {
        BoltSlackReply reply = makeReply();

        reply.send("a".repeat(LIMIT) + "b");

        assertThat(reply.ackBody()).hasValueSatisfying(body -> assertThat(body.getText()).isEqualTo("a".repeat(LIMIT)));
        assertThat(posted).extracting(SlashCommandResponse::getText).containsExactly("b");
    }

    @Test
    void aFailedPost_isDroppedWithoutThrowing() {
        BoltSlackReply reply = new BoltSlackReply(message -> {
            throw new IOException("network detail");
        });

        reply.ack();
        reply.send("answer");

        assertThat(reply.ackBody()).isEmpty();
    }

    private BoltSlackReply makeReply() {
        return new BoltSlackReply(message -> {
            posted.add(message);
            return WebhookResponse.builder().code(200).build();
        });
    }
}
