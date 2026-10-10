package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "mindforge.security.secure-cookies=false",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none",
    "mindforge.rate-limits.requests=2",
    "mindforge.rate-limits.window=PT3S"
})
class RateLimitTest extends TestContainerBase {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void aModelCallingEndpointAnswers429WithRetryAfterOnceTheAllowanceIsSpentAndRecoversAfterTheWindow()
        throws Exception {
        String cookie = register();
        String lint = "/api/v1/knowledge-bases/" + UUID.randomUUID() + "/lint-runs";

        HttpResponse<String> first = post(lint, cookie);
        assertThat(first.statusCode()).isNotEqualTo(429);
        assertThat(first.headers().firstValue("RateLimit-Limit")).contains("2");
        assertThat(first.headers().firstValue("RateLimit-Remaining")).contains("1");
        assertThat(post(lint, cookie).headers().firstValue("RateLimit-Remaining")).contains("0");

        HttpResponse<String> limited = post(lint, cookie);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(limited.headers().firstValue("RateLimit-Reset")).isPresent();
        assertThat(limited.body()).contains("RATE_LIMITED");

        Thread.sleep(3_200);

        assertThat(post(lint, cookie).statusCode()).isNotEqualTo(429);
    }

    private String register() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Ala\",\"email\":\"ala-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"sekretne-haslo\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        Matcher token = Pattern.compile("token=([^;]*)")
            .matcher(String.join(";", response.headers().allValues("Set-Cookie")));
        assertThat(token.find()).isTrue();
        return "token=" + token.group(1);
    }

    private HttpResponse<String> post(String path, String cookie) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).header("Cookie", cookie)
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
