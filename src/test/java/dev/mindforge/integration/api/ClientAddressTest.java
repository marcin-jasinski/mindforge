package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.support.TestContainerBase;

/**
 * Real Tomcat (MockMvc bypasses RemoteIpValve). The client connects from loopback, which is in Spring's default
 * {@code server.tomcat.remoteip.internal-proxies}, so its {@code X-Forwarded-*} headers are trusted.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "mindforge.security.secure-cookies=false",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none",
    "mindforge.rate-limits.auth.requests=2",
    "mindforge.rate-limits.auth.window=PT30S"
})
class ClientAddressTest extends TestContainerBase {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    // AC2

    @Test
    void aTrustedPeerIsLimitedAsTheRightmostForwardedAddress() throws Exception {
        assertThat(registerFrom("1.2.3.4, 5.6.7.8").statusCode()).isEqualTo(201);
        assertThat(registerFrom("1.2.3.4, 5.6.7.8").statusCode()).isEqualTo(201);

        assertThat(registerFrom("9.9.9.9").statusCode()).isNotEqualTo(429);
        assertThat(registerFrom("5.6.7.8").statusCode()).isEqualTo(429);
    }

    // AC4

    @Test
    void forwardedHttpsFromATrustedPeerMakesTheOAuthRedirectUriHttps() throws Exception {
        HttpResponse<String> redirect = HTTP.send(HttpRequest.newBuilder(uri("/oauth2/authorization/github"))
            .header("X-Forwarded-Proto", "https").GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).hasValueSatisfying(location ->
            assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
                .contains("redirect_uri=https://localhost/login/oauth2/code/github"));
    }

    private HttpResponse<String> registerFrom(String forwardedFor) throws Exception {
        String json = "{\"displayName\":\"Alice\",\"email\":\"alice-" + UUID.randomUUID()
            + "@example.com\",\"password\":\"secret-password\"}";
        return HTTP.send(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
            .header("Content-Type", "application/json").header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
