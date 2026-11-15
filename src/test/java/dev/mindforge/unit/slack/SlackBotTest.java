package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import dev.mindforge.slack.SlackBot;

class SlackBotTest {

    private static final String BOT_TOKEN = "xoxb-placeholder";
    private static final String APP_TOKEN = "xapp-placeholder";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger botLog = (Logger) LoggerFactory.getLogger(SlackBot.class);

    {
        logs.start();
        botLog.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        botLog.detachAppender(logs);
    }

    // AC5

    @Test
    void run_startsTheSocketModeAppWithoutBlocking() throws Exception {
        SocketModeApp socketModeApp = mock(SocketModeApp.class);

        new SlackBot(socketModeApp).run(null);

        verify(socketModeApp).startAsync();
        verify(socketModeApp, never()).start();
    }

    @Test
    void close_closesTheSocketModeApp() throws Exception {
        SocketModeApp socketModeApp = mock(SocketModeApp.class);

        new SlackBot(socketModeApp).close();

        verify(socketModeApp).close();
    }

    @Test
    void run_logsAFailureToStartWithoutAnyTokenValue() throws Exception {
        SocketModeApp socketModeApp = mock(SocketModeApp.class);
        doThrow(new IllegalArgumentException("The token is invalid: " + BOT_TOKEN + " " + APP_TOKEN))
            .when(socketModeApp).startAsync();

        new SlackBot(socketModeApp).run(null);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("IllegalArgumentException")
                .doesNotContain(BOT_TOKEN, APP_TOKEN);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
