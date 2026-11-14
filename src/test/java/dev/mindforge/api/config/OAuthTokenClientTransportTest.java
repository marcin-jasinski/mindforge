package dev.mindforge.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;

/** The OAuth2 token exchange stays on the JDK transport although Apache HttpClient 5 is on the classpath (D04). */
class OAuthTokenClientTransportTest {

    private static final String REDIRECT_URI = "http://localhost/login/oauth2/code/github";

    @Test
    void theTokenExchange_usesTheJdkHttpClientAndStillReadsTheTokenResponse() throws IOException {
        List<String> userAgents = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", exchange -> {
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = "{\"access_token\":\"token\",\"token_type\":\"bearer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            OAuth2AccessTokenResponse response = SecurityConfig.authorizationCodeTokenClient()
                .getTokenResponse(makeGrantRequest("http://localhost:" + server.getAddress().getPort() + "/token"));

            assertThat(response.getAccessToken().getTokenValue()).isEqualTo("token");
            assertThat(userAgents).singleElement().asString().startsWith("Java-http-client");
        } finally {
            server.stop(0);
        }
    }

    private static OAuth2AuthorizationCodeGrantRequest makeGrantRequest(String tokenUri) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(REDIRECT_URI)
            .authorizationUri("http://localhost/authorize")
            .tokenUri(tokenUri)
            .build();
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("http://localhost/authorize")
            .clientId("client")
            .redirectUri(REDIRECT_URI)
            .state("state")
            .build();
        OAuth2AuthorizationResponse authorization = OAuth2AuthorizationResponse.success("code")
            .redirectUri(REDIRECT_URI)
            .state("state")
            .build();
        return new OAuth2AuthorizationCodeGrantRequest(registration,
            new OAuth2AuthorizationExchange(request, authorization));
    }
}
