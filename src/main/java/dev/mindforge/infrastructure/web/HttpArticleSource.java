package dev.mindforge.infrastructure.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import dev.mindforge.domain.model.ArticleFetchException;
import dev.mindforge.domain.model.ArticleFetchException.Reason;
import dev.mindforge.domain.model.FetchedArticle;
import dev.mindforge.domain.port.ArticleSource;
import dev.mindforge.infrastructure.security.EgressDeniedException;
import dev.mindforge.infrastructure.security.EgressPolicy;

/**
 * Fetches one article over HTTP with Apache HttpClient 5 (ADR 0025). Every host is resolved through
 * {@link CheckedDnsResolver}, the client's own redirect handling is off and each of at most {@value #MAX_REDIRECTS}
 * redirects is checked by {@link EgressPolicy} before it is requested. One deadline covers all hops: connects wait at
 * most the time left, and a watchdog cancels the request when the deadline passes, so a server that trickles headers
 * or body cannot hold a fetch open. Response header lines and header count are capped. Only an HTML response of at
 * most the body cap is converted; every per-URL failure is an {@link ArticleFetchException}.
 */
public final class HttpArticleSource implements ArticleSource, AutoCloseable {

    static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final Set<String> HTML_TYPES = Set.of("text/html", "application/xhtml+xml");
    static final int MAX_HEADER_LINE_LENGTH = 8192;
    static final int MAX_HEADER_COUNT = 100;

    private final EgressPolicy egressPolicy;
    private final CloseableHttpClient client;
    private final Duration requestTimeout;
    private final long maxBodyBytes;
    private final HtmlArticleConverter converter = new HtmlArticleConverter();
    private final ScheduledExecutorService watchdog = watchdog();

    private HttpArticleSource(EgressPolicy egressPolicy, CloseableHttpClient client, Duration requestTimeout,
                              long maxBodyBytes) {
        this.egressPolicy = egressPolicy;
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.maxBodyBytes = maxBodyBytes;
    }

    /** Builds the source and its client: checked DNS, no redirect handling, no retries, cookies or system proxy. */
    public static HttpArticleSource create(EgressPolicy egressPolicy, Duration connectTimeout, Duration requestTimeout,
                                           long maxBodyBytes) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.of(connectTimeout))
            .build();
        ManagedHttpClientConnectionFactory connectionFactory = ManagedHttpClientConnectionFactory.builder()
            .http1Config(Http1Config.custom()
                .setMaxLineLength(MAX_HEADER_LINE_LENGTH)
                .setMaxHeaderCount(MAX_HEADER_COUNT)
                .build())
            .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
            new DeadlineConnectionOperator(new CheckedDnsResolver(egressPolicy)), PoolConcurrencyPolicy.STRICT,
            PoolReusePolicy.LIFO, TimeValue.NEG_ONE_MILLISECOND, connectionFactory);
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .build();
        return new HttpArticleSource(egressPolicy, client, requestTimeout, maxBodyBytes);
    }

    @Override
    public FetchedArticle fetch(URI url) {
        long deadline = System.nanoTime() + requestTimeout.toNanos();
        URI current = check(url.toString());
        for (int redirects = 0; ; redirects++) {
            HttpGet request = new HttpGet(current);
            Timeout left = DeadlineConnectionOperator.timeLeft(deadline);
            request.setConfig(RequestConfig.custom().setResponseTimeout(left).build());
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute(DeadlineConnectionOperator.DEADLINE, deadline);
            // A response timeout restarts on every byte; the watchdog closes the connection at the deadline itself.
            ScheduledFuture<?> abort = watchdog.schedule(request::cancel, left.toMilliseconds(), TimeUnit.MILLISECONDS);
            try (ClassicHttpResponse response = client.executeOpen(null, request, context)) {
                try {
                    int status = response.getCode();
                    if (!REDIRECT_STATUSES.contains(status)) {
                        return article(current, status, response.getEntity(), deadline);
                    }
                    if (redirects == MAX_REDIRECTS) {
                        throw new ArticleFetchException(Reason.REDIRECTS);
                    }
                    current = next(current, response.getFirstHeader(HttpHeaders.LOCATION));
                } finally {
                    // Drops the connection instead of draining a body that was not read to its end.
                    request.cancel();
                }
            } catch (EgressDeniedException e) {
                throw new ArticleFetchException(Reason.POLICY);
            } catch (IOException e) {
                throw new ArticleFetchException(failure(e, deadline));
            } finally {
                abort.cancel(false);
            }
        }
    }

    @Override
    public void close() throws IOException {
        watchdog.shutdownNow();
        client.close();
    }

    private static ScheduledExecutorService watchdog() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("article-fetch-deadline").factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /** A failure after the deadline, or a cancelled request, is a timeout whatever the client reported. */
    private static Reason failure(IOException e, long deadline) {
        if (e instanceof InterruptedIOException || deadline - System.nanoTime() <= 0) {
            return Reason.TIMEOUT;
        }
        return e instanceof MessageConstraintException ? Reason.TOO_LARGE : Reason.UNREACHABLE;
    }

    private URI check(String url) {
        try {
            return egressPolicy.check(url);
        } catch (EgressDeniedException e) {
            throw new ArticleFetchException(Reason.POLICY);
        }
    }

    private URI next(URI current, Header location) {
        if (location == null) {
            throw new ArticleFetchException(Reason.HTTP_STATUS);
        }
        try {
            return check(current.resolve(new URI(location.getValue())).toString());
        } catch (URISyntaxException e) {
            throw new ArticleFetchException(Reason.POLICY);
        }
    }

    private FetchedArticle article(URI finalUrl, int status, HttpEntity entity, long deadline) throws IOException {
        if (status < 200 || status > 299) {
            throw new ArticleFetchException(Reason.HTTP_STATUS);
        }
        ContentType contentType = entity == null ? null : ContentType.parseLenient(entity.getContentType());
        if (contentType == null || !HTML_TYPES.contains(contentType.getMimeType().toLowerCase(Locale.ROOT))) {
            throw new ArticleFetchException(Reason.CONTENT_TYPE);
        }
        if (entity.getContentLength() > maxBodyBytes) {
            throw new ArticleFetchException(Reason.TOO_LARGE);
        }
        String html = new String(readCapped(entity.getContent(), deadline), charset(contentType));
        ArticleText converted = converter.convert(html, finalUrl.toString());
        if (converted.blocks().isEmpty()) {
            throw new ArticleFetchException(Reason.EMPTY);
        }
        return new FetchedArticle(finalUrl, converted.title(), converted.text(), converted.blocks());
    }

    /** Reads at most the cap and stops at the deadline; reading one byte more fails, so a body is never truncated. */
    private byte[] readCapped(InputStream body, long deadline) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read = body.read(buffer); read >= 0; read = body.read(buffer)) {
            bytes.write(buffer, 0, read);
            if (bytes.size() > maxBodyBytes) {
                throw new ArticleFetchException(Reason.TOO_LARGE);
            }
            DeadlineConnectionOperator.timeLeft(deadline);
        }
        return bytes.toByteArray();
    }

    private static Charset charset(ContentType contentType) {
        try {
            return contentType.getCharset(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
