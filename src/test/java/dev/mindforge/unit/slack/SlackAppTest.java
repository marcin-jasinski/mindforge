package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.middleware.Middleware;
import dev.mindforge.slack.SlackApp;
import dev.mindforge.slack.SlackMessageListener;
import dev.mindforge.slack.SlackUploadHandler;

class SlackAppTest {

    // AC10

    @Test
    void theMessageListener_runsAfterBoltsOwnMiddleware() {
        SlackMessageListener listener = new SlackMessageListener(mock(SlackUploadHandler.class));
        AppConfig config = AppConfig.builder().build();

        List<Middleware> middleware =
            ReflectionTestUtils.invokeMethod(new SlackApp(config, listener), "buildDefaultMiddlewareList", config);

        assertThat(middleware).hasSizeGreaterThan(1).last().isSameAs(listener);
    }
}
