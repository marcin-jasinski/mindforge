package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.support.TestContainerBase;

/** No peer is a trusted proxy ({@code ^$} matches no address), so the loopback client's X-Forwarded-For is ignored. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "mindforge.security.secure-cookies=false",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none",
    "mindforge.rate-limits.auth.requests=2",
    "mindforge.rate-limits.auth.window=PT30S",
    "server.tomcat.remoteip.internal-proxies=^$"
})
class UntrustedProxyClientAddressTest extends TestContainerBase {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    // AC3

    @Test
    void aForgedForwardedAddressFromAnUntrustedPeerDoesNotGiveANewBucket() throws Exception {
        assertThat(registerFrom("1.1.1.1").statusCode()).isEqualTo(201);
        assertThat(registerFrom("2.2.2.2").statusCode()).isEqualTo(201);

        assertThat(registerFrom("3.3.3.3").statusCode()).isEqualTo(429);
    }

    private HttpResponse<String> registerFrom(String forwardedFor) throws Exception {
        String json = "{\"displayName\":\"Alice\",\"email\":\"alice-" + UUID.randomUUID()
            + "@example.com\",\"password\":\"secret-password\"}";
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
            .header("Content-Type", "application/json").header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
