package dev.mindforge.slack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.slack.SlackFileRejectedException.Reason;

/**
 * The only class that downloads a Slack file (ADR 0029). Slack is the operator-configured platform, so the Slack SDK's
 * OkHttp client fetches it with the bot token, but only from a fixed host list over {@code https} on the default port,
 * and only to the upload size limit: a larger file fails and is never truncated. Redirects are not followed, so the
 * token never leaves the host list; a redirect is a failed download.
 */
public final class SlackFiles {

    private static final Logger log = LoggerFactory.getLogger(SlackFiles.class);

    private static final Set<String> FILE_HOSTS = Set.of("files.slack.com");
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private final OkHttpClient client;
    private final String botToken;
    private final long maxBytes;

    public SlackFiles(OkHttpClient client, String botToken, long maxBytes) {
        this.client = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(DOWNLOAD_TIMEOUT)
            .build();
        this.botToken = botToken;
        this.maxBytes = maxBytes;
    }

    /**
     * @param url the file's {@code url_private_download}
     * @param declaredSize the file's size as Slack reports it
     * @return the file's bytes, at most the upload size limit
     * @throws SlackFileRejectedException when the URL is not on Slack's file host, the file is over the limit, or the
     *         download fails
     */
    public byte[] read(String url, long declaredSize) {
        HttpUrl fileUrl = requireFileHost(url);
        String host = fileUrl.host();
        if (declaredSize > maxBytes) {
            throw new SlackFileRejectedException(Reason.TOO_LARGE);
        }
        Request request = new Request.Builder().url(fileUrl).header("Authorization", "Bearer " + botToken).build();
        byte[] content;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Slack file download failed: host={} status={}", host, response.code());
                throw new SlackFileRejectedException(Reason.DOWNLOAD_FAILED);
            }
            try (InputStream in = response.body().byteStream()) {
                content = in.readNBytes((int) Math.min(MAX_ARRAY_LENGTH, maxBytes + 1));
            }
        } catch (IOException e) {
            log.warn("Slack file download failed: host={} error={}", host, e.getClass().getName());
            throw new SlackFileRejectedException(Reason.DOWNLOAD_FAILED, e);
        }
        if (content.length > maxBytes) {
            throw new SlackFileRejectedException(Reason.TOO_LARGE);
        }
        return content;
    }

    /**
     * Checks the URL as both {@link URI} and OkHttp parse it, so the host that receives the token is the host that was
     * checked. Logs the host only: the URL's path and query are not logged.
     */
    private static HttpUrl requireFileHost(String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url);
        } catch (URISyntaxException e) {
            log.warn("Slack file refused: URL does not parse");
            throw new SlackFileRejectedException(Reason.NOT_FROM_SLACK, e);
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        boolean admitted = "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443) && host != null && FILE_HOSTS.contains(host);
        HttpUrl parsed = admitted ? HttpUrl.parse(url) : null;
        if (parsed == null || !parsed.isHttps() || parsed.port() != 443 || !host.equals(parsed.host())) {
            log.warn("Slack file refused: host={}", host);
            throw new SlackFileRejectedException(Reason.NOT_FROM_SLACK);
        }
        return parsed;
    }
}
