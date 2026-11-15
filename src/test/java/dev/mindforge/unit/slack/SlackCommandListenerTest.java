package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.RequestHeaders;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.util.json.GsonFactory;
import dev.mindforge.slack.SlackCall;
import dev.mindforge.slack.SlackCommandHandler;
import dev.mindforge.slack.SlackCommandListener;
import dev.mindforge.slack.SlackReply;

class SlackCommandListenerTest {

    private final SlackCommandHandler handler = mock(SlackCommandHandler.class);

    // AC6

    @Test
    void aCommandWithoutUserTeam_usesTheWorkspaceAsTheCallerTeam() {
        SlackCall call = handle("""
            {"team_id":"T0HOME","user_id":"U0ADA","user_name":"ada","command":"/mf-ask","text":"why?"}""");

        assertThat(call).isEqualTo(new SlackCall("T0HOME", "T0HOME", "U0ADA", "ada", "/mf-ask", "why?"));
    }

    @Test
    void aCommandWithUserTeam_usesItAsTheCallerTeam() {
        SlackCall call = handle("""
            {"team_id":"T0HOME","user_team":"T0PARTNER","user_id":"U0ADA","user_name":"ada","command":"/mf-ask"}""");

        assertThat(call.workspaceTeamId()).isEqualTo("T0HOME");
        assertThat(call.callerTeamId()).isEqualTo("T0PARTNER");
    }

    @Test
    void aUserTeamThatIsNotAString_givesNoCallerTeam() {
        SlackCall call = handle("""
            {"team_id":"T0HOME","user_team":{"id":"T0PARTNER"},"user_id":"U0ADA","command":"/mf-ask"}""");

        assertThat(call.callerTeamId()).isNull();
    }

    // AC9

    @Test
    void aTextSentBeforeTheAck_isTheEphemeralAckBody() {
        doAnswer(invocation -> {
            invocation.<SlackReply>getArgument(1).send("This bot is not available here.");
            return null;
        }).when(handler).handle(any(), any());

        Response response = makeListenerResponse("""
            {"team_id":"T0OTHER","user_id":"U0ADA","command":"/mf-ask"}""");

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"response_type\":\"ephemeral\"")
            .contains("This bot is not available here.");
    }

    private SlackCall handle(String body) {
        makeListenerResponse(body);
        ArgumentCaptor<SlackCall> call = ArgumentCaptor.forClass(SlackCall.class);
        verify(handler).handle(call.capture(), any());
        return call.getValue();
    }

    private Response makeListenerResponse(String body) {
        SlashCommandPayload payload = GsonFactory.createSnakeCase().fromJson(body, SlashCommandPayload.class);
        SlashCommandRequest request = new SlashCommandRequest(body, payload, new RequestHeaders(Map.of()));
        return new SlackCommandListener(handler).apply(request, new SlashCommandContext());
    }
}
