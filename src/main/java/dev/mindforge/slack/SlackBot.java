package dev.mindforge.slack;

import com.slack.api.bolt.socket_mode.SocketModeApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Starts the Socket Mode app when the application starts, without blocking, and closes it when the context closes. Once
 * connected, Bolt's session monitor reconnects by itself after the socket closes.
 */
public class SlackBot implements ApplicationRunner, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SlackBot.class);

    private final SocketModeApp socketModeApp;

    public SlackBot(SocketModeApp socketModeApp) {
        this.socketModeApp = socketModeApp;
    }

    /** A failure leaves the rest of the application running; only the exception type is logged, never a token. */
    @Override
    public void run(ApplicationArguments args) {
        try {
            socketModeApp.startAsync();
            log.info("Slack bot connected via Socket Mode");
        } catch (Exception e) {
            log.error("Slack bot failed to start Socket Mode ({}); check SLACK_BOT_TOKEN and SLACK_APP_TOKEN",
                e.getClass().getName());
        }
    }

    @Override
    public void close() throws Exception {
        socketModeApp.close();
    }
}
