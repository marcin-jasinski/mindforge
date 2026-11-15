package dev.mindforge.slack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;

/** Translates Bolt slash-command requests into {@link SlackCall}s and returns the ack; holds no logic of its own. */
public class SlackCommandListener implements SlashCommandHandler {

    private static final String USER_TEAM = "user_team";

    private final SlackCommandHandler handler;

    public SlackCommandListener(SlackCommandHandler handler) {
        this.handler = handler;
    }

    @Override
    public Response apply(SlashCommandRequest request, SlashCommandContext context) {
        BoltSlackReply reply = new BoltSlackReply(context::respond);
        handler.handle(toCall(request), reply);
        return reply.ackBody().map(context::ack).orElseGet(context::ack);
    }

    static SlackCall toCall(SlashCommandRequest request) {
        SlashCommandPayload payload = request.getPayload();
        return new SlackCall(payload.getTeamId(), callerTeamId(request.getRequestBodyAsString(), payload.getTeamId()),
            payload.getUserId(), payload.getUserName(), payload.getCommand(), payload.getText());
    }

    /**
     * {@code user_team} when the payload carries it, else {@code team_id} (ADR 0034). Bolt's payload type has no
     * {@code user_team}, so it is read from the Socket Mode JSON body. A {@code user_team} that is not a string gives
     * null, which the allowlist rejects.
     */
    static String callerTeamId(String body, String teamId) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (JsonParseException e) {
            return teamId;
        }
        if (!root.isJsonObject()) {
            return teamId;
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement userTeam = object.get(USER_TEAM);
        if (userTeam == null || userTeam.isJsonNull()) {
            return teamId;
        }
        return userTeam.isJsonPrimitive() && userTeam.getAsJsonPrimitive().isString() ? userTeam.getAsString() : null;
    }
}
