package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import dev.mindforge.slack.SlackFileRejectedException;
import dev.mindforge.slack.SlackFileRejectedException.Reason;
import dev.mindforge.slack.SlackFiles;

/** No test reaches Slack: an interceptor answers in place of the network, or rewrites to a loopback server. */
class SlackFilesTest {

    private static final long MAX_BYTES = 16;
    private static final String BOT_TOKEN = "xoxb-secret-token";
    private static final String FILE_URL = "https://files.slack.com/files-pri/T1-F1/download/notes.md?pub_secret=sig";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger filesLog = (Logger) LoggerFactory.getLogger(SlackFiles.class);

    {
        logs.start();
        filesLog.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        filesLog.detachAppender(logs);
    }

    // AC1

    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.example.org/files-pri/T1-F1/download/notes.md",
        "http://files.slack.com/files-pri/T1-F1/download/notes.md",
        "https://user:pass@files.slack.com/files-pri/T1-F1/download/notes.md",
        "https://files.slack.com:8443/files-pri/T1-F1/download/notes.md",
        "https://files.slack.com.evil.example.org/files-pri/T1-F1/download/notes.md",
        "not a url"})
    void aUrlOffTheFileHostRule_isRefusedWithoutARequest(String url) {
        FakeNetwork network = makeNetwork(200, new byte[4]);

        assertThatThrownBy(() -> makeFiles(network).read(url, 4))
            .isInstanceOf(SlackFileRejectedException.class)
            .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.NOT_FROM_SLACK);
        assertThat(network.requests).isEmpty();
    }

    @Test
    void theFileHostOnPort443InUpperCase_isAccepted() {
        FakeNetwork network = makeNetwork(200, new byte[] {1, 2, 3});

        assertThat(makeFiles(network).read("https://FILES.slack.com:443/files-pri/T1-F1/download/a.md", 3))
            .containsExactly(1, 2, 3);
    }

    // AC2

    @Test
    void aDeclaredSizeOverTheCap_isTooLargeWithoutARequest() {
        FakeNetwork network = makeNetwork(200, new byte[1]);

        assertThatThrownBy(() -> makeFiles(network).read(FILE_URL, MAX_BYTES + 1))
            .isInstanceOf(SlackFileRejectedException.class)
            .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.TOO_LARGE);
        assertThat(network.requests).isEmpty();
    }

    @Test
    void aBodyLongerThanTheCap_isTooLargeAndNotTruncated() {
        FakeNetwork network = makeNetwork(200, new byte[(int) MAX_BYTES * 4]);

        assertThatThrownBy(() -> makeFiles(network).read(FILE_URL, 1))
            .isInstanceOf(SlackFileRejectedException.class)
            .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void aBodyOfExactlyTheCap_isReturned() {
        byte[] body = new byte[(int) MAX_BYTES];
        body[0] = 7;

        assertThat(makeFiles(makeNetwork(200, body)).read(FILE_URL, MAX_BYTES)).isEqualTo(body);
    }

    // AC3

    @Test
    void aGoodUrl_isRequestedWithTheBotTokenAsBearerAndReturnsTheBytes() {
        FakeNetwork network = makeNetwork(200, new byte[] {4, 5});

        assertThat(makeFiles(network).read(FILE_URL, 2)).containsExactly(4, 5);
        assertThat(network.requests).singleElement().satisfies(request -> {
            assertThat(request.url().toString()).isEqualTo(FILE_URL);
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + BOT_TOKEN);
        });
    }

    // AC4

    @Test
    void aNon2xxStatus_isADownloadFailureLoggedByHostOnly() {
        FakeNetwork network = makeNetwork(403, new byte[0]);

        assertThatThrownBy(() -> makeFiles(network).read(FILE_URL, 4))
            .isInstanceOf(SlackFileRejectedException.class)
            .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.DOWNLOAD_FAILED);
        assertHostOnlyWarning();
    }

    @Test
    void aNetworkError_isADownloadFailureLoggedByHostOnly() {
        FakeNetwork network = makeFailingNetwork(new IOException("connection reset " + FILE_URL));

        assertThatThrownBy(() -> makeFiles(network).read(FILE_URL, 4))
            .isInstanceOf(SlackFileRejectedException.class)
            .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.DOWNLOAD_FAILED);
        assertHostOnlyWarning();
    }

    @Test
    void aTimeout_isADownloadFailure() {
        FakeNetwork network = makeFailingNetwork(new InterruptedIOException("timeout"));

        assertThatThrownBy(() -> makeFiles(network).read(FILE_URL, 4))
            .isInstanceOf(SlackFileRejectedException.class)
            .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.DOWNLOAD_FAILED);
    }

    @Test
    void aRefusedUrl_isLoggedByHostOnly() {
        assertThatThrownBy(() -> makeFiles(makeNetwork(200, new byte[1]))
            .read("https://evil.example.org/download/a.md?pub_secret=sig", 1))
            .isInstanceOf(SlackFileRejectedException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("evil.example.org").doesNotContain("sig", "/a.md");
        });
    }

    // AC5

    @Test
    void aRedirectToAnotherHost_isNotFollowedAndIsADownloadFailure() throws IOException {
        List<String> received = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            received.add(exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders()
                .getFirst("Authorization"));
            if (exchange.getRequestURI().getPath().startsWith("/files-pri")) {
                exchange.getResponseHeaders().add("Location", "http://localhost:" + port + "/elsewhere");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(200, 1);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(1);
                }
            }
            exchange.close();
        });
        server.start();
        try {
            OkHttpClient toLoopback = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                    .url(chain.request().url().newBuilder().scheme("http").host("127.0.0.1").port(port).build())
                    .build()))
                .build();

            assertThatThrownBy(() -> new SlackFiles(toLoopback, BOT_TOKEN, MAX_BYTES).read(FILE_URL, 1))
                .isInstanceOf(SlackFileRejectedException.class)
                .extracting(e -> ((SlackFileRejectedException) e).reason()).isEqualTo(Reason.DOWNLOAD_FAILED);
            assertThat(received)
                .containsExactly("/files-pri/T1-F1/download/notes.md Bearer " + BOT_TOKEN);
        } finally {
            server.stop(0);
        }
    }

    private void assertHostOnlyWarning() {
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("files.slack.com")
                .doesNotContain(BOT_TOKEN, "sig", "notes.md");
        });
    }

    private static SlackFiles makeFiles(FakeNetwork network) {
        return new SlackFiles(new OkHttpClient.Builder().addInterceptor(network).build(), BOT_TOKEN, MAX_BYTES);
    }

    private static FakeNetwork makeNetwork(int status, byte[] body) {
        return new FakeNetwork(status, body, null);
    }

    private static FakeNetwork makeFailingNetwork(IOException error) {
        return new FakeNetwork(0, null, error);
    }

    /** Answers in place of the network and records every request it sees. */
    private static final class FakeNetwork implements Interceptor {

        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final int status;
        private final byte[] body;
        private final IOException error;

        private FakeNetwork(int status, byte[] body, IOException error) {
            this.status = status;
            this.body = body;
            this.error = error;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            requests.add(chain.request());
            if (error != null) {
                throw error;
            }
            return new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("fake")
                .body(ResponseBody.create(body, null))
                .build();
        }
    }
}
