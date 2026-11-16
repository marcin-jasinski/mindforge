package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.support.TestContainerBase;

/** Every request comes from the loopback address, so the tests share one bucket; each starts after a full window. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "mindforge.security.secure-cookies=false",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none",
    "mindforge.rate-limits.auth.requests=3",
    "mindforge.rate-limits.auth.window=PT3S"
})
class AuthRateLimitTest extends TestContainerBase {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String PASSWORD = "secret-password";

    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void waitForAFreshWindow() throws InterruptedException {
        Thread.sleep(3_200);
    }

    // AC1, AC2, AC4

    @Test
    void failedSignInsSpendTheAllowanceAndTheNextSignInGets429EvenWithTheRightPassword() throws Exception {
        String email = newEmail();
        HttpResponse<String> registered = register(email);
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.headers().firstValue("RateLimit-Limit")).contains("3");
        assertThat(registered.headers().firstValue("RateLimit-Remaining")).contains("2");
        assertThat(registered.headers().firstValue("RateLimit-Reset")).isPresent();

        HttpResponse<String> wrong = login(email, "wrong-password");
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(wrong.headers().firstValue("RateLimit-Remaining")).contains("1");
        assertThat(login(email, "wrong-password").headers().firstValue("RateLimit-Remaining")).contains("0");

        HttpResponse<String> limited = login(email, PASSWORD);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValueSatisfying(seconds ->
            assertThat(Long.parseLong(seconds)).isBetween(1L, 3L));
        assertThat(limited.headers().firstValue("RateLimit-Limit")).contains("3");
        assertThat(limited.headers().firstValue("RateLimit-Remaining")).contains("0");
        assertThat(limited.body()).contains("\"RATE_LIMITED\"").contains("Too many requests; retry in");
    }

    // AC3

    @Test
    void signInAndSignUpShareOneAllowance() throws Exception {
        String email = newEmail();
        assertThat(register(email).statusCode()).isEqualTo(201);
        assertThat(login(email, PASSWORD).statusCode()).isEqualTo(200);
        assertThat(register(newEmail()).statusCode()).isEqualTo(201);

        assertThat(register(newEmail()).statusCode()).isEqualTo(429);
        assertThat(login(email, PASSWORD).statusCode()).isEqualTo(429);
    }

    // AC5

    @Test
    void getRequestsAreNotCountedAndSignInWorksAgainAfterTheWindow() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(get("/api/v1/auth/providers").statusCode()).isNotEqualTo(429);
            assertThat(get("/api/v1/auth/me").statusCode()).isNotEqualTo(429);
        }
        String email = newEmail();
        assertThat(register(email).statusCode()).isEqualTo(201);
        assertThat(login(email, PASSWORD).statusCode()).isEqualTo(200);
        assertThat(login(email, PASSWORD).statusCode()).isEqualTo(200);
        assertThat(login(email, PASSWORD).statusCode()).isEqualTo(429);

        Thread.sleep(3_200);

        assertThat(login(email, PASSWORD).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> register(String email) throws Exception {
        return post("/api/v1/auth/register",
            "{\"displayName\":\"Alice\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String newEmail() {
        return "alice-" + UUID.randomUUID() + "@example.com";
    }
}
