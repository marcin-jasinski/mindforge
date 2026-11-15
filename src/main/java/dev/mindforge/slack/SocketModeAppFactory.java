package dev.mindforge.slack;

import java.io.IOException;

import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;

/**
 * Builds the Socket Mode app. The default is Bolt's constructor, which opens no socket until the app is started; a test
 * supplies a fake bean of this type so that nothing touches the network.
 */
@FunctionalInterface
public interface SocketModeAppFactory {

    SocketModeApp create(String appToken, SocketModeClient.Backend backend, App app) throws IOException;
}
