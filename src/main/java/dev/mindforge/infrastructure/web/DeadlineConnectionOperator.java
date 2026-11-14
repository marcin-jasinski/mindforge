package dev.mindforge.infrastructure.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;

import dev.mindforge.domain.model.ArticleFetchException;
import dev.mindforge.domain.model.ArticleFetchException.Reason;

/**
 * Keeps the connect phase inside the fetch deadline that {@link HttpArticleSource} puts in the request context: each
 * connect attempt waits at most the time left, and no further resolved address is tried once the deadline has passed.
 */
final class DeadlineConnectionOperator extends DefaultHttpClientConnectionOperator {

    /** The context attribute that holds the fetch deadline as a {@link System#nanoTime()} value. */
    static final String DEADLINE = "mindforge.article.deadline";

    DeadlineConnectionOperator(DnsResolver dnsResolver) {
        super(DefaultSchemePortResolver.INSTANCE, dnsResolver, RegistryBuilder.<TlsSocketStrategy>create()
            .register(URIScheme.HTTPS.id, DefaultClientTlsStrategy.createDefault())
            .build());
    }

    @Override
    public void connect(ManagedHttpClientConnection conn, HttpHost endpointHost, NamedEndpoint endpointName,
                        Path unixDomainSocket, InetSocketAddress localAddress, Timeout connectTimeout,
                        SocketConfig socketConfig, Object attachment, HttpContext context) throws IOException {
        Timeout left = timeLeft(context);
        Timeout timeout = left == null || (connectTimeout != null && connectTimeout.compareTo(left) < 0)
            ? connectTimeout : left;
        super.connect(conn, endpointHost, endpointName, unixDomainSocket, localAddress, timeout, socketConfig,
            attachment, context);
    }

    @Override
    protected void onBeforeSocketConnect(HttpContext context, HttpHost host) {
        timeLeft(context);
    }

    /** The time left as a timeout of at least 1 ms (0 would mean no timeout), or null when no deadline is set. */
    static Timeout timeLeft(HttpContext context) {
        Object deadline = context == null ? null : context.getAttribute(DEADLINE);
        return deadline instanceof Long nanos ? timeLeft(nanos) : null;
    }

    /** The time left before the deadline, rounded up to whole milliseconds; TIMEOUT when it has passed. */
    static Timeout timeLeft(long deadline) {
        long left = deadline - System.nanoTime();
        if (left <= 0) {
            throw new ArticleFetchException(Reason.TIMEOUT);
        }
        return Timeout.ofMilliseconds(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left + 999_999)));
    }
}
