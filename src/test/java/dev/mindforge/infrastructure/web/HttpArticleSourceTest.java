package dev.mindforge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.mindforge.domain.model.ArticleFetchException;
import dev.mindforge.domain.model.ArticleFetchException.Reason;
import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.FetchedArticle;
import dev.mindforge.infrastructure.security.EgressPolicy;
import dev.mindforge.infrastructure.security.TestEgressPolicies;

class HttpArticleSourceTest {

    private static final String HOST = "articles.test";
    private static final String PAGE = """
        <html><head><title>Mitosis</title></head>
        <body><h1>Phases</h1><p>Prophase comes first.</p></body></html>
        """;
    private static final long MAX_BODY_BYTES = 4096;

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private HttpServer server;
    private HttpArticleSource source;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        if (source != null) {
            source.close();
        }
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void anAdmittedHtmlPage_isConvertedWithItsFinalUrlTitleTextAndBlocks() {
        serve("/article", html(200, "text/html", PAGE));

        FetchedArticle article = admittingSource().fetch(url("/article"));

        assertThat(article.finalUrl()).isEqualTo(url("/article"));
        assertThat(article.title()).isEqualTo("Mitosis");
        assertThat(article.text()).isEqualTo("Phases\n\nProphase comes first.");
        assertThat(article.blocks())
            .extracting(ContentBlock::blockType, ContentBlock::content)
            .containsExactly(
                tuple(BlockType.HEADING, "Phases"),
                tuple(BlockType.TEXT, "Prophase comes first."));
    }

    @Test
    void aHostResolvingToLoopback_isRefusedInDnsResolutionBeforeAnyRequest() {
        serve("/article", html(200, "text/html", PAGE));
        source = make(TestEgressPolicies.strictAddresses(HOST, InetAddress.getLoopbackAddress(), port()),
            Duration.ofSeconds(5));

        assertThat(failure(() -> source.fetch(url("/article")))).isEqualTo(Reason.POLICY);
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://169.254.169.254/", "http://127.0.0.2:%d/article"})
    void aRedirectToANonPublicAddress_isRefusedBeforeItIsRequested(String target) {
        serve("/start", redirect(String.format(target, port())));
        serve("/article", html(200, "text/html", PAGE));

        assertThat(failure(() -> admittingSource().fetch(url("/start")))).isEqualTo(Reason.POLICY);
        assertThat(requests).containsExactly("/start");
    }

    @Test
    void aSixthRedirect_failsWithTooManyRedirects() {
        for (int hop = 0; hop < 6; hop++) {
            serve("/hop" + hop, redirect("/hop" + (hop + 1)));
        }
        serve("/hop6", html(200, "text/html", PAGE));

        assertThat(failure(() -> admittingSource().fetch(url("/hop0")))).isEqualTo(Reason.REDIRECTS);
        assertThat(requests).hasSize(6);
    }

    @Test
    void fiveRedirects_reachTheArticleAndRelativeLocationsResolveAgainstTheCurrentUri() {
        serve("/a/hop0", redirect("hop1"));
        serve("/a/hop1", redirect("/b/hop2"));
        serve("/b/hop2", redirect(url("/c/hop3").toString()));
        serve("/c/hop3", redirect("../d/hop4"));
        serve("/d/hop4", redirect("/final?page=1"));
        serve("/final", html(200, "text/html", PAGE));

        FetchedArticle article = admittingSource().fetch(url("/a/hop0"));

        assertThat(article.finalUrl()).isEqualTo(url("/final?page=1"));
        assertThat(requests).containsExactly("/a/hop0", "/a/hop1", "/b/hop2", "/c/hop3", "/d/hop4", "/final");
    }

    @Test
    void aResponseSlowerThanTheRequestTimeout_failsWithTimeout() {
        serve("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            html(200, "text/html", PAGE).handle(exchange);
        });
        source = make(admitting(), Duration.ofMillis(300));

        assertThat(failure(() -> source.fetch(url("/slow")))).isEqualTo(Reason.TIMEOUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1 200 OK\r\nX-Slow: ", "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n"
        + "Transfer-Encoding: chunked\r\n\r\n1;ext="})
    void aServerThatTricklesHeadersOrAChunkLine_failsWithTimeoutAtTheDeadline(String prefix) throws IOException {
        try (ServerSocket trickler = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            executor.submit(() -> trickle(trickler, prefix));
            source = make(TestEgressPolicies.admitting(HOST, InetAddress.getLoopbackAddress(), trickler.getLocalPort()),
                Duration.ofMillis(500));
            long start = System.nanoTime();

            Reason reason = failure(() -> source.fetch(
                URI.create("http://" + HOST + ":" + trickler.getLocalPort() + "/article")));

            assertThat(reason).isEqualTo(Reason.TIMEOUT);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test
    void aHeaderLineOverTheLimit_failsWithTooLarge() {
        serve("/article", exchange -> {
            exchange.getResponseHeaders().add("X-Long", "a".repeat(HttpArticleSource.MAX_HEADER_LINE_LENGTH));
            html(200, "text/html", PAGE).handle(exchange);
        });

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void moreHeadersThanTheLimit_failWithTooLarge() {
        serve("/article", exchange -> {
            for (int header = 0; header <= HttpArticleSource.MAX_HEADER_COUNT; header++) {
                exchange.getResponseHeaders().add("X-Header-" + header, "value");
            }
            html(200, "text/html", PAGE).handle(exchange);
        });

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.TOO_LARGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "text/plain"})
    void aNonHtmlContentType_failsWithContentType(String contentType) {
        serve("/article", html(200, contentType, PAGE));

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.CONTENT_TYPE);
    }

    @Test
    void aMissingContentType_failsWithContentType() {
        serve("/article", html(200, null, PAGE));

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.CONTENT_TYPE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html; charset=UTF-8", "TEXT/HTML", "application/xhtml+xml"})
    void anHtmlContentType_isAccepted(String contentType) {
        serve("/article", html(200, contentType, PAGE));

        assertThat(admittingSource().fetch(url("/article")).title()).isEqualTo("Mitosis");
    }

    @Test
    void aBodyOverTheCapWithContentLength_failsWithTooLarge() {
        serve("/big", respond(200, "text/html", oversizedPage(), false));

        assertThat(failure(() -> admittingSource().fetch(url("/big")))).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void aChunkedBodyOverTheCap_failsWithTooLarge() {
        serve("/big", respond(200, "text/html", oversizedPage(), true));

        assertThat(failure(() -> admittingSource().fetch(url("/big")))).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void aBodyOfExactlyTheCap_isAccepted() {
        String page = PAGE + " ".repeat((int) MAX_BODY_BYTES - PAGE.length());
        serve("/full", respond(200, "text/html", page, true));

        assertThat(admittingSource().fetch(url("/full")).title()).isEqualTo("Mitosis");
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void anErrorStatus_failsWithHttpStatus(int status) {
        serve("/article", html(status, "text/html", PAGE));

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.HTTP_STATUS);
    }

    @Test
    void aRedirectWithoutLocation_failsWithHttpStatus() {
        serve("/article", exchange -> {
            requests.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.HTTP_STATUS);
    }

    @Test
    void aRefusedConnection_failsWithUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        source = make(TestEgressPolicies.admitting(HOST, InetAddress.getLoopbackAddress(), closedPort),
            Duration.ofSeconds(5));

        assertThat(failure(() -> source.fetch(URI.create("http://" + HOST + ":" + closedPort + "/article"))))
            .isEqualTo(Reason.UNREACHABLE);
    }

    @Test
    void aPageWithNoText_failsWithEmpty() {
        serve("/article", html(200, "text/html", "<html><head><title>Mitosis</title></head><body></body></html>"));

        assertThat(failure(() -> admittingSource().fetch(url("/article")))).isEqualTo(Reason.EMPTY);
    }

    @Test
    void aFailureMessage_containsNeitherTheBodyNorTheUrlQuery() {
        serve("/article", html(500, "text/html", "<p>secret-body-marker</p>"));

        ArticleFetchException failure = catchThrowableOfType(ArticleFetchException.class,
            () -> admittingSource().fetch(url("/article?token=secret-query-marker")));

        assertThat(failure.getMessage())
            .doesNotContain("secret-body-marker")
            .doesNotContain("secret-query-marker")
            .doesNotContain(HOST);
    }

    private HttpArticleSource admittingSource() {
        source = make(admitting(), Duration.ofSeconds(5));
        return source;
    }

    private EgressPolicy admitting() {
        return TestEgressPolicies.admitting(HOST, InetAddress.getLoopbackAddress(), port());
    }

    private static HttpArticleSource make(EgressPolicy policy, Duration requestTimeout) {
        return HttpArticleSource.create(policy, Duration.ofSeconds(2), requestTimeout, MAX_BODY_BYTES);
    }

    private static Reason failure(Runnable fetch) {
        return catchThrowableOfType(ArticleFetchException.class, fetch::run).reason();
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private URI url(String pathAndQuery) {
        return URI.create("http://" + HOST + ":" + port() + pathAndQuery);
    }

    private void serve(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            handler.handle(exchange);
        });
    }

    private HttpHandler html(int status, String contentType, String body) {
        return respond(status, contentType, body, false);
    }

    private HttpHandler redirect(String location) {
        return exchange -> {
            requests.add(exchange.getRequestURI().getPath());
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        };
    }

    private HttpHandler respond(int status, String contentType, String body, boolean chunked) {
        return (HttpExchange exchange) -> {
            requests.add(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException e) {
                // The client may drop the connection once it has read enough.
            }
            exchange.close();
        };
    }

    /** Accepts one connection, reads the request line, then sends the prefix and one byte every 100 ms. */
    private static void trickle(ServerSocket trickler, String prefix) {
        try (Socket socket = trickler.accept()) {
            InputStream in = socket.getInputStream();
            in.read(new byte[4096]);
            OutputStream out = socket.getOutputStream();
            out.write(prefix.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            for (int i = 0; i < 100; i++) {
                Thread.sleep(100);
                out.write('a');
                out.flush();
            }
        } catch (IOException e) {
            // The client closes the connection at its deadline.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String oversizedPage() {
        return PAGE + "<p>" + "x".repeat((int) MAX_BODY_BYTES) + "</p>";
    }
}
