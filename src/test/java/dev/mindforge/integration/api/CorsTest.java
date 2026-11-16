package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.support.TestContainerBase;

/**
 * Preflights of the sign-in route, with no allowed origin and with one. Each nested context sets its own origin, so
 * neither can be shared; both are closed after use so their connection pools do not stay open for later test classes.
 */
@Tag("integration")
class CorsTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // AC2

    @Nested
    @DirtiesContext
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(properties = {
        "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
        "mindforge.security.secure-cookies=false",
        "spring.ai.openai.api-key=test-placeholder",
        "spring.jpa.hibernate.ddl-auto=none",
        "mindforge.security.cors-allowed-origin="
    })
    class WithoutAnAllowedOrigin extends TestContainerBase {

        @Value("${local.server.port}")
        private int port;

        @Test
        void aPreflightFromAnyOriginGetsNoAllowOriginHeader() throws Exception {
            HttpResponse<String> response = preflight(port, "http://evil.example");

            assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        }
    }

    // AC3

    @Nested
    @DirtiesContext
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(properties = {
        "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
        "mindforge.security.secure-cookies=false",
        "spring.ai.openai.api-key=test-placeholder",
        "spring.jpa.hibernate.ddl-auto=none",
        "mindforge.security.cors-allowed-origin=https://app.example"
    })
    class WithOneAllowedOrigin extends TestContainerBase {

        @Value("${local.server.port}")
        private int port;

        @Test
        void aPreflightFromTheAllowedOriginIsAdmittedWithCredentials() throws Exception {
            HttpResponse<String> response = preflight(port, "https://app.example");

            assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("https://app.example");
            assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        }

        @Test
        void aPreflightFromAnotherOriginGetsNoAllowOriginHeader() throws Exception {
            HttpResponse<String> response = preflight(port, "http://evil.example");

            assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        }
    }

    private static HttpResponse<String> preflight(int port, String origin) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
