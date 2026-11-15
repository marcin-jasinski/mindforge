package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.slack.api.bolt.middleware.MiddlewareChain;
import com.slack.api.bolt.request.Request;
import com.slack.api.bolt.request.RequestHeaders;
import com.slack.api.bolt.request.builtin.EventRequest;
import com.slack.api.bolt.response.Response;
import dev.mindforge.slack.SlackMessage;
import dev.mindforge.slack.SlackMessageListener;
import dev.mindforge.slack.SlackUploadHandler;

class SlackMessageListenerTest {

    private final SlackUploadHandler handler = mock(SlackUploadHandler.class);
    private final MiddlewareChain chain = mock(MiddlewareChain.class);

    // AC2, AC3

    @Test
    void aFileShareInADirectMessage_isTranslatedWithTheEventTeamsAndAcknowledged() throws Exception {
        SlackMessage message = handle("""
            {"team_id":"T0ENVELOPE","type":"event_callback","event":{"type":"message","subtype":"file_share",
             "team":"T0HOME","user_team":"T0PARTNER","user":"U0ADA","channel":"D0DM","channel_type":"im",
             "text":"lesson_id=osmosis","files":[{"name":"notes.md","mimetype":"text/markdown","size":10,
             "url_private_download":"https://files.slack.com/files-pri/T0HOME-F01/download/notes.md"}]}}""");

        assertThat(message).isEqualTo(new SlackMessage("T0HOME", "T0PARTNER", "U0ADA", "D0DM", "im", "file_share",
            null, "lesson_id=osmosis", List.of(new SlackMessage.File("notes.md", "text/markdown", 10,
                "https://files.slack.com/files-pri/T0HOME-F01/download/notes.md"))));
    }

    @Test
    void anEventWithoutTeam_usesTheEnvelopeTeam_andAMissingUserTeamStaysNull() throws Exception {
        SlackMessage message = handle("""
            {"team_id":"T0HOME","event":{"type":"message","subtype":"file_share","user":"U0ADA",
             "channel_type":"im","bot_id":"B0BOT","files":[{"name":"notes.md"}]}}""");

        assertThat(message.workspaceTeamId()).isEqualTo("T0HOME");
        assertThat(message.callerTeamId()).isNull();
        assertThat(message.botIdOrNull()).isEqualTo("B0BOT");
        assertThat(message.files()).containsExactly(new SlackMessage.File("notes.md", null, 0, null));
    }

    @Test
    void aUserTeamThatIsNotAString_givesNoCallerTeam() throws Exception {
        SlackMessage message = handle("""
            {"team_id":"T0HOME","event":{"type":"message","user_team":{"id":"T0PARTNER"},"user":"U0ADA"}}""");

        assertThat(message.callerTeamId()).isNull();
        assertThat(message.files()).isEmpty();
    }

    @Test
    void anotherEvent_passesThroughWithoutTheHandler() throws Exception {
        EventRequest request = makeRequest("""
            {"team_id":"T0HOME","event":{"type":"app_mention","user":"U0ADA"}}""");
        Response next = Response.ok();
        when(chain.next(request)).thenReturn(next);

        Response response = new SlackMessageListener(handler).apply(request, Response.builder().build(), chain);

        assertThat(response).isSameAs(next);
        verifyNoInteractions(handler);
    }

    private SlackMessage handle(String body) throws Exception {
        Response response = new SlackMessageListener(handler).apply(makeRequest(body), Response.builder().build(),
            chain);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(chain, never()).next(any(Request.class));
        ArgumentCaptor<SlackMessage> message = ArgumentCaptor.forClass(SlackMessage.class);
        verify(handler).handle(message.capture(), any());
        return message.getValue();
    }

    private static EventRequest makeRequest(String body) {
        return new EventRequest(body, new RequestHeaders(Map.of()));
    }
}
