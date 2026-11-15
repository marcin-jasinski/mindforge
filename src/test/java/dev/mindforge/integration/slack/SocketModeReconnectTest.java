package dev.mindforge.integration.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * ADR 0037: with the Java-WebSocket backend, a started Socket Mode app opens a new socket by itself after the server
 * closes the current one (Bolt's session monitor checks every five seconds). A local Web API and a local WebSocket
 * server stand in for Slack; placeholder tokens.
 */
@Tag("integration")
class SocketModeReconnectTest {

    private static final long RECONNECT_WAIT_SECONDS = 30;

    private final LinkedBlockingQueue<WebSocket> opened = new LinkedBlockingQueue<>();
    private WebSocketServer socketServer;
    private HttpServer webApi;
    private SocketModeApp socketModeApp;

    @AfterEach
    void stopServers() throws Exception {
        if (socketModeApp != null) {
            socketModeApp.close();
        }
        if (socketServer != null) {
            socketServer.stop(1000);
        }
        if (webApi != null) {
            webApi.stop(0);
        }
    }

    // AC6

    @Test
    void aStartedSocketModeApp_reconnectsAfterTheServerClosesTheSocket() throws Exception {
        socketServer = startSocketServer();
        webApi = startWebApi("ws://127.0.0.1:" + socketServer.getPort() + "/");
        socketModeApp = new SocketModeApp("xapp-placeholder", SocketModeClient.Backend.JavaWebSocket,
            makeApp("http://127.0.0.1:" + webApi.getAddress().getPort() + "/api/"));

        socketModeApp.startAsync();
        WebSocket first = opened.poll(RECONNECT_WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(first).as("first connection").isNotNull();

        first.close(1001, "server going away");

        assertThat(opened.poll(RECONNECT_WAIT_SECONDS, TimeUnit.SECONDS)).as("connection after close").isNotNull();
    }

    private WebSocketServer startSocketServer() throws InterruptedException {
        LinkedBlockingQueue<WebSocket> opens = opened;
        CountDownLatch started = new CountDownLatch(1);
        WebSocketServer server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 0)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                opens.add(conn);
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
            }

            @Override
            public void onStart() {
                started.countDown();
            }
        };
        server.start();
        assertThat(started.await(10, TimeUnit.SECONDS)).as("WebSocket server started").isTrue();
        return server;
    }

    private static HttpServer startWebApi(String socketUrl) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth.test", exchange -> respond(exchange,
            "{\"ok\":true,\"user_id\":\"U1\",\"team_id\":\"T1\",\"bot_id\":\"B1\"}"));
        server.createContext("/api/apps.connections.open", exchange -> respond(exchange,
            "{\"ok\":true,\"url\":\"" + socketUrl + "\"}"));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static App makeApp(String webApiPrefix) {
        SlackConfig config = new SlackConfig();
        config.setMethodsEndpointUrlPrefix(webApiPrefix);
        return new App(AppConfig.builder().singleTeamBotToken("xoxb-placeholder").slack(Slack.getInstance(config))
            .build());
    }
}
