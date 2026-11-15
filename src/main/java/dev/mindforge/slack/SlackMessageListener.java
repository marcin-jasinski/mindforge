package dev.mindforge.slack;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.slack.api.bolt.middleware.Middleware;
import com.slack.api.bolt.middleware.MiddlewareChain;
import com.slack.api.bolt.request.Request;
import com.slack.api.bolt.request.builtin.EventRequest;
import com.slack.api.bolt.response.Response;

/**
 * Translates every Bolt {@code message} event into a {@link SlackMessage}, hands it to the {@link SlackUploadHandler}
 * and acknowledges it; holds no logic of its own. It is a middleware, not an event handler, because Bolt's typed
 * message events drop {@code team}, {@code user_team} and {@code bot_id}, which are read from the JSON body. Other
 * requests pass through.
 */
public class SlackMessageListener implements Middleware {

    private static final String MESSAGE = "message";

    private final SlackUploadHandler handler;

    public SlackMessageListener(SlackUploadHandler handler) {
        this.handler = handler;
    }

    @Override
    public Response apply(Request req, Response resp, MiddlewareChain chain) throws Exception {
        if (!(req instanceof EventRequest event) || !MESSAGE.equals(event.getEventType())) {
            return chain.next(req);
        }
        SlackMessage message = toMessage(event.getRequestBodyAsString());
        handler.handle(message, new BoltSlackDirectMessageReply(text -> event.getContext().client()
            .chatPostMessage(post -> post.channel(message.channelId()).text(text))));
        return Response.ok();
    }

    /**
     * The event in an Events API body. A missing or non-string field is null, so a malformed body gives a message the
     * handler ignores or the allowlist rejects.
     */
    static SlackMessage toMessage(String body) {
        JsonObject root = object(parse(body));
        JsonObject event = object(root.get("event"));
        String team = string(event, "team");
        List<SlackMessage.File> files = new ArrayList<>();
        JsonElement fileList = event.get("files");
        if (fileList != null && fileList.isJsonArray()) {
            fileList.getAsJsonArray().forEach(element -> files.add(toFile(object(element))));
        }
        return new SlackMessage(team == null ? string(root, "team_id") : team, string(event, "user_team"),
            string(event, "user"), string(event, "channel"), string(event, "channel_type"), string(event, "subtype"),
            string(event, "bot_id"), string(event, "text"), files);
    }

    private static SlackMessage.File toFile(JsonObject file) {
        JsonElement size = file.get("size");
        long declaredSize = size != null && size.isJsonPrimitive() && size.getAsJsonPrimitive().isNumber()
            ? size.getAsLong() : 0;
        return new SlackMessage.File(string(file, "name"), string(file, "mimetype"), declaredSize,
            string(file, "url_private_download"));
    }

    private static JsonElement parse(String body) {
        try {
            return JsonParser.parseString(body);
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString() : null;
    }
}
