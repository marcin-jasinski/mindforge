package dev.mindforge.slack;

import java.util.ArrayList;
import java.util.List;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.middleware.Middleware;

/**
 * The Bolt app with the {@link SlackMessageListener} after Bolt's own middleware. {@link App#use} would build Bolt's
 * middleware at once, which checks the bot token over the network; Bolt builds it here only when the app starts.
 */
public class SlackApp extends App {

    private final SlackMessageListener messageListener;

    public SlackApp(AppConfig config, SlackMessageListener messageListener) {
        super(config);
        this.messageListener = messageListener;
    }

    @Override
    protected List<Middleware> buildDefaultMiddlewareList(AppConfig config) {
        List<Middleware> middleware = new ArrayList<>(super.buildDefaultMiddlewareList(config));
        middleware.add(messageListener);
        return middleware;
    }
}
