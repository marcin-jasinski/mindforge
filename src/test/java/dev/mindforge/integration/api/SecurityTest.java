package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;
import dev.mindforge.support.TestContainerBase;

/**
 * Plan task 20.4 over real HTTP: a session cookie this server did not issue unchanged is anonymous, another user's
 * knowledge base is forbidden, and a foreign id under one's own knowledge base is not found. The properties are
 * {@link ApiFlowTest}'s, so both classes share one cached context.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "mindforge.security.secure-cookies=false",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none",
    "mindforge.rate-limits.auth.requests=1000",
    "springdoc.api-docs.enabled=true"
})
class SecurityTest extends TestContainerBase {

    private static final Pattern FORBIDDEN_FIELDS = Pattern.compile(
        "\"(cost|stepVersions|step_versions|passwordHash|referenceAnswer|groundingContext|rawPrompt|rawCompletion|token|groundingExcerpt)\"");
    private static final Pattern SESSION_COOKIE = Pattern.compile("token=([^;]*)");
    private static final String NOTES = "# Mitosis\n\nMitosis is cell division.\n";
    private static final String BODY = "# Course\n\nMitosis splits a cell in two.\n";
    private static final String PAGE = "/pages/concepts/mitosis";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Value("${local.server.port}")
    private int port;

    @Value("${mindforge.security.jwt-secret}")
    private String jwtSecret;

    @Autowired
    private StubAIGateway gateway;

    // AC1, AC5

    @Test
    void aSessionCookieWithAnAlteredSignatureIsRefused() {
        Client owner = signedUp();
        String kb = createKnowledgeBase(owner);
        String[] parts = owner.sessionToken.split("\\.");
        char first = parts[2].charAt(0);
        String altered = parts[0] + "." + parts[1] + "." + (first == 'A' ? 'B' : 'A') + parts[2].substring(1);

        Response genuine = owner.get("/api/v1/auth/me");
        Response refused = new Client(altered).get("/api/v1/auth/me");
        Response refusedKnowledgeBase = new Client(altered).get(kb + "/index");

        assertThat(genuine.status()).isEqualTo(200);
        assertThat(refused.status()).isEqualTo(401);
        assertThat(refusedKnowledgeBase.status()).isEqualTo(401);
        List.of(genuine, refused, refusedKnowledgeBase).forEach(SecurityTest::assertNoSensitiveFields);
    }

    // AC2, AC5

    @Test
    void aWronglySignedExpiredUnsignedOrMalformedSessionCookieIsRefused() {
        Client owner = signedUp();
        String kb = createKnowledgeBase(owner);
        String userId = owner.get("/api/v1/auth/me").json().get("userId").asString();
        Instant now = Instant.now();
        String wrongKey = Jwts.builder().subject(userId).issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofHours(1))))
            .signWith(Keys.hmacShaKeyFor("another-secret-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8)))
            .compact();
        String expired = Jwts.builder().subject(userId).issuedAt(Date.from(now.minus(Duration.ofHours(2))))
            .expiration(Date.from(now.minus(Duration.ofHours(1))))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();
        String unsigned = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
            + base64Url("{\"sub\":\"" + userId + "\",\"exp\":" + now.plus(Duration.ofHours(1)).getEpochSecond() + "}")
            + ".";

        for (String token : List.of(wrongKey, expired, unsigned, "not-a-session-token")) {
            for (String path : List.of("/api/v1/auth/me", kb + "/index")) {
                Response refused = new Client(token).get(path);
                assertThat(refused.status()).as("%s with %s", path, token).isEqualTo(401);
                assertNoSensitiveFields(refused);
            }
        }
    }

    // AC3, AC5

    @Test
    void anotherUserIsForbiddenTheOwnersRunReportRevertPageRevisionsAndExport() {
        Client owner = signedUp();
        String kb = createKnowledgeBase(owner);
        String runId = ingested(owner, kb);
        Client other = signedUp();

        List<Response> responses = List.of(other.get(kb + "/runs/" + runId),
            other.post(kb + "/runs/" + runId + "/revert", Map.of()), other.get(kb + PAGE + "/revisions"));

        responses.forEach(response -> assertThat(response.status()).as(response.body()).isEqualTo(403));
        responses.forEach(SecurityTest::assertNoSensitiveFields);
        HttpResponse<byte[]> export = other.download(kb + "/export");
        assertThat(export.statusCode()).isEqualTo(403);
        assertThat(FORBIDDEN_FIELDS.matcher(new String(export.body(), StandardCharsets.UTF_8)).find()).isFalse();
        assertThat(owner.get(kb + "/index").json().get("pages")).as("the revert did not run").hasSize(2);
    }

    // AC4, AC5

    @Test
    void aForeignRunIdOrPagePathUnderOnesOwnKnowledgeBaseIsNotFoundAndShowsNothingOfTheOwner() {
        Client owner = signedUp();
        String kb = createKnowledgeBase(owner);
        String runId = ingested(owner, kb);
        Client other = signedUp();
        String ownKb = createKnowledgeBase(other);
        String foreignKbId = kb.substring(kb.lastIndexOf('/') + 1);

        Response report = other.get(ownKb + "/runs/" + runId);
        Response revert = other.post(ownKb + "/runs/" + runId + "/revert", Map.of());
        Response revisions = other.get(ownKb + PAGE + "/revisions");

        for (Response response : List.of(report, revert, revisions)) {
            assertThat(response.status()).as(response.body()).isEqualTo(404);
            assertThat(response.json().get("code").asString()).isEqualTo("NOT_FOUND");
            assertThat(response.body()).doesNotContain(foreignKbId).doesNotContain("Mitosis").doesNotContain("biology")
                .doesNotContain("Cell division");
            assertNoSensitiveFields(response);
        }
        assertThat(report.body()).doesNotContain(runId).doesNotContain("concepts/mitosis");
        assertThat(revert.body()).doesNotContain(runId).doesNotContain("concepts/mitosis");
        assertThat(owner.get(kb + "/index").json().get("pages")).as("the revert did not run").hasSize(2);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Uploads the notes into the knowledge base and returns the completed ingest run's id. */
    private String ingested(Client client, String kb) {
        givenAnswers();
        String documentId = client.upload(kb, "biology.md", NOTES).json().get("documentId").asString();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            JsonNode run = client.get(kb + "/documents/" + documentId).json().get("latestRun");
            String status = run == null || run.isNull() ? null : run.get("status").asString();
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                assertThat(status).as("run ended with %s", run.get("failureReason")).isEqualTo("COMPLETED");
                return run.get("runId").asString();
            }
            assertThat(System.nanoTime()).as("Run of %s is still %s", documentId, status).isLessThan(deadline);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private void givenAnswers() {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("text", "Mitosis is cell division.");
        claim.put("title", "Mitosis");
        claim.put("targetPath", null);
        claim.put("firstBlock", 0);
        claim.put("lastBlock", 1);
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, Prompts.json(Map.of("claims", List.of(claim), "chunkDigest", "Mitosis.")));
        gateway.answer(Prompts.WRITE, Prompts.json(Map.of("description", "Cell division.", "body", BODY)));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    private Client signedUp() {
        Client client = new Client(null);
        Response registered = client.post("/api/v1/auth/register", Map.of("displayName", "Student",
            "email", "student-" + UUID.randomUUID() + "@example.com", "password", "secret-password"));
        assertThat(registered.status()).isEqualTo(201);
        assertNoSensitiveFields(registered);
        return client;
    }

    private static String createKnowledgeBase(Client client) {
        Response created = client.post("/api/v1/knowledge-bases", Map.of("name", "Biology", "description", "Notes"));
        assertThat(created.status()).isEqualTo(201);
        return "/api/v1/knowledge-bases/" + created.json().get("kbId").asString();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertNoSensitiveFields(Response response) {
        assertThat(FORBIDDEN_FIELDS.matcher(response.body()).find()).as(response.body()).isFalse();
    }

    private record Response(int status, String body) {

        JsonNode json() {
            return JSON.readTree(body);
        }
    }

    /** A browser stand-in that sends a session cookie and keeps the one the server sets. */
    private final class Client {

        private final HttpClient http = HttpClient.newHttpClient();
        private String sessionToken;

        Client(String sessionToken) {
            this.sessionToken = sessionToken;
        }

        Response get(String path) {
            return send(request(path).GET());
        }

        Response post(String path, Object body) {
            return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
        }

        Response upload(String kb, String filename, String markdown) {
            String boundary = "mindforge-" + UUID.randomUUID();
            String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/markdown\r\n\r\n" + markdown + "\r\n"
                + "--" + boundary + "--\r\n";
            return send(request(kb + "/documents").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        }

        HttpResponse<byte[]> download(String path) {
            try {
                return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (Exception e) {
                throw new AssertionError("Request failed: " + e, e);
            }
        }

        private HttpRequest.Builder request(String path) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(TIMEOUT);
            if (sessionToken != null && !sessionToken.isEmpty()) {
                builder.header("Cookie", "token=" + sessionToken);
            }
            return builder;
        }

        private Response send(HttpRequest.Builder request) {
            try {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                for (String cookie : new ArrayList<>(response.headers().allValues("Set-Cookie"))) {
                    Matcher token = SESSION_COOKIE.matcher(cookie);
                    if (token.lookingAt()) {
                        sessionToken = token.group(1);
                    }
                }
                return new Response(response.statusCode(), response.body());
            } catch (Exception e) {
                throw new AssertionError("Request failed: " + e, e);
            }
        }
    }
}
