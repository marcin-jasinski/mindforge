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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "mindforge.security.secure-cookies=false",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class ApiFlowTest extends TestContainerBase {

    private static final Pattern FORBIDDEN_FIELDS = Pattern.compile(
        "\"(cost|stepVersions|step_versions|passwordHash|referenceAnswer|groundingContext|rawPrompt|rawCompletion|token)\"");
    private static final Pattern SESSION_COOKIE = Pattern.compile("token=([^;]*)");
    private static final String NOTES = "# Mitoza\n\nMitoza to podział komórki.\n";
    private static final String BODY = "# Przebieg\n\nMitoza dzieli komórkę na dwie.\n";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private StubAIGateway gateway;

    // ---------------------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------------------

    @Test
    void registerSetsAnHttpOnlyCookieThatSignsInUntilLogout() {
        Client client = new Client();
        String email = "ala-" + UUID.randomUUID() + "@example.com";

        Response registered = client.post("/api/auth/register",
            Map.of("displayName", "Ala", "email", email, "password", "sekretne-haslo"));

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.setCookie()).contains("token=").contains("HttpOnly").contains("SameSite=Lax");
        assertNoSensitiveFields(registered);
        assertThat(client.get("/api/auth/me").json().get("email").asString()).isEqualTo(email);
        assertThat(client.post("/api/auth/logout", Map.of()).status()).isEqualTo(204);
        assertThat(client.get("/api/auth/me").status()).isEqualTo(401);

        Client other = new Client();
        assertThat(other.post("/api/auth/login", Map.of("email", email, "password", "zle-haslo")).status())
            .isEqualTo(401);
        assertThat(other.post("/api/auth/login", Map.of("email", email.toUpperCase(), "password", "sekretne-haslo"))
            .status()).isEqualTo(200);
        assertThat(other.get("/api/auth/me").status()).isEqualTo(200);
    }

    // ---------------------------------------------------------------------------
    // Upload and the wiki
    // ---------------------------------------------------------------------------

    @Test
    void anUploadIngestsIntoPagesAndNoResponseCarriesASensitiveField() {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        givenAnswers();

        Response upload = client.upload(kb, "biologia.md", NOTES, false);

        assertThat(upload.status()).isEqualTo(202);
        String documentId = upload.json().get("documentId").asString();
        awaitRun(client, kb, documentId, "COMPLETED");

        Response index = client.get(kb + "/index");
        assertThat(index.json().get("pages").findValuesAsString("path"))
            .containsExactlyInAnyOrder("concepts/mitoza", "sources/biologia");
        Response page = client.get(kb + "/pages/concepts/mitoza");
        assertThat(page.json().get("markdown").asString())
            .startsWith(BODY).contains("# Citations\n\n[1] [biologia](/sources/biologia.md)");
        Response runs = client.get(kb + "/runs");
        String runId = runs.json().get(0).get("runId").asString();
        Response report = client.get(kb + "/runs/" + runId);
        assertThat(report.json().get("revertOffered").asBoolean()).isTrue();
        assertThat(report.json().get("pages")).hasSize(2);

        List<Response> responses = List.of(upload, index, page, runs, report, client.get(kb),
            client.get(kb + "/documents"), client.get(kb + "/documents/" + documentId), client.get(kb + "/graph"),
            client.get(kb + "/health"), client.get(kb + "/pages/concepts/mitoza/revisions"), client.get("/api/auth/me"));
        responses.forEach(response -> assertThat(response.status()).as(response.body()).isLessThan(300));
        responses.forEach(ApiFlowTest::assertNoSensitiveFields);

        Response reverted = client.post(kb + "/runs/" + runId + "/revert", Map.of());
        assertThat(reverted.status()).isEqualTo(200);
        assertThat(client.get(kb + "/index").json().get("pages")).isEmpty();
        assertThat(client.post(kb + "/runs/" + runId + "/revert", Map.of()).json().get("code").asString())
            .isEqualTo("REVERT_NOT_ALLOWED");
    }

    @Test
    void theOpenApiDocumentIsServed() {
        Response docs = new Client().get("/v3/api-docs");

        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.json().get("openapi").asString()).startsWith("3.");
    }

    @Test
    void anUploadOfAnExistingLessonIsAConflictUnlessItIsANewVersion() {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        givenAnswers();
        assertThat(client.upload(kb, "biologia.md", NOTES, false).status()).isEqualTo(202);

        Response collision = client.upload(kb, "biologia.md", NOTES + "\nPowtórka.\n", false);

        assertThat(collision.status()).isEqualTo(409);
        assertThat(collision.json().get("code").asString()).isEqualTo("LESSON_EXISTS");
        assertThat(collision.json().get("lessonId").asString()).isEqualTo("biologia");
        assertThat(collision.json().get("lessonTitle").asString()).isEqualTo("biologia");
        assertThat(client.upload(kb, "biologia.md", NOTES + "\nPowtórka.\n", true).status()).isEqualTo(202);
    }

    // ---------------------------------------------------------------------------
    // Queue, retry and deletion
    // ---------------------------------------------------------------------------

    @Test
    void aBusyKnowledgeBaseCannotBeDeletedWhileAQueuedUploadAndARetryRunInOrder() throws Exception {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        gateway.reset();
        gateway.answer("Paragon sklepowy", "{\"relevant\": false, \"reason\": \"To paragon.\", \"confidence\": 0.9}");
        givenAnswers();
        String receipt = client.upload(kb, "paragon.md", "Paragon sklepowy.\n", false).json().get("documentId").asString();
        awaitRun(client, kb, receipt, "FAILED");

        CountDownLatch extracting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        gateway.reset();
        gateway.answer("] Pierwsza lekcja", prompt -> {
            extracting.countDown();
            await(release);
            return extraction();
        });
        givenAnswers();
        String first = client.upload(kb, "lekcja-1.md", "Pierwsza lekcja o komórkach.\n", false).json()
            .get("documentId").asString();
        assertThat(extracting.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        String queued = client.upload(kb, "lekcja-2.md", "Druga lekcja o komórkach.\n", false).json()
            .get("documentId").asString();
        Response retry = client.post(kb + "/documents/" + receipt + "/runs", Map.of());

        assertThat(retry.status()).isEqualTo(202);
        assertThat(client.post(kb + "/documents/" + receipt + "/runs", Map.of()).status()).isEqualTo(409);
        Response busy = client.delete(kb);
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.json().get("code").asString()).isEqualTo("KNOWLEDGE_BASE_BUSY");

        release.countDown();
        awaitRun(client, kb, first, "COMPLETED");
        awaitRun(client, kb, queued, "COMPLETED");
        awaitRun(client, kb, receipt, "COMPLETED");
        JsonNode runs = client.get(kb + "/runs").json();
        assertThat(finishedAt(runs, retry.json().get("runId").asString()))
            .isAfter(finishedAt(runs, runOf(client, kb, queued)));

        assertThat(client.delete(kb).status()).isEqualTo(204);
        assertThat(client.get(kb).status()).isEqualTo(404);
    }

    // ---------------------------------------------------------------------------
    // Ownership
    // ---------------------------------------------------------------------------

    @Test
    void aUserCannotReachAnotherUsersKnowledgeBase() {
        Client owner = signedUp();
        String kb = createKnowledgeBase(owner);
        Client other = signedUp();

        for (String path : List.of(kb, kb + "/index", kb + "/pages/concepts/mitoza", kb + "/runs", kb + "/progress",
            kb + "/health", kb + "/documents", kb + "/graph")) {
            assertThat(other.get(path).status()).as(path).isEqualTo(403);
        }
        assertThat(other.delete(kb).status()).isEqualTo(403);
        assertThat(other.upload(kb, "biologia.md", NOTES, false).status()).isEqualTo(403);
        assertThat(other.post(kb + "/lint-runs", Map.of()).status()).isEqualTo(403);
        assertThat(new Client().get(kb + "/index").status()).isEqualTo(401);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void givenAnswers() {
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction());
        gateway.answer(Prompts.WRITE, Prompts.json(Map.of("description", "Podział komórki.", "body", BODY)));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    private static String extraction() {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("text", "Mitoza to podział komórki.");
        claim.put("title", "Mitoza");
        claim.put("targetPath", null);
        claim.put("firstBlock", 0);
        claim.put("lastBlock", 1);
        return Prompts.json(Map.of("claims", List.of(claim), "chunkDigest", "Mitoza."));
    }

    private Client signedUp() {
        Client client = new Client();
        Response registered = client.post("/api/auth/register", Map.of("displayName", "Uczeń",
            "email", "uczen-" + UUID.randomUUID() + "@example.com", "password", "sekretne-haslo"));
        assertThat(registered.status()).isEqualTo(201);
        return client;
    }

    private static String createKnowledgeBase(Client client) {
        Response created = client.post("/api/knowledge-bases", Map.of("name", "Biologia", "description", "Notatki"));
        assertThat(created.status()).isEqualTo(201);
        return "/api/knowledge-bases/" + created.json().get("kbId").asString();
    }

    private static void awaitRun(Client client, String kb, String documentId, String status) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            JsonNode run = client.get(kb + "/documents/" + documentId).json().get("latestRun");
            String current = run == null || run.isNull() ? null : run.get("status").asString();
            if ("COMPLETED".equals(current) || "FAILED".equals(current)) {
                assertThat(current).as("run ended with %s", run.get("failureReason")).isEqualTo(status);
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Run of " + documentId + " is still " + current);
            }
            await(Duration.ofMillis(100));
        }
    }

    private static String runOf(Client client, String kb, String documentId) {
        return client.get(kb + "/documents/" + documentId).json().get("latestRun").get("runId").asString();
    }

    private static Instant finishedAt(JsonNode runs, String runId) {
        for (JsonNode run : runs) {
            if (run.get("runId").asString().equals(runId)) {
                return Instant.parse(run.get("finishedAt").asString());
            }
        }
        throw new AssertionError("No run " + runId);
    }

    private static void assertNoSensitiveFields(Response response) {
        assertThat(FORBIDDEN_FIELDS.matcher(response.body()).find()).as(response.body()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Response(int status, String body, List<String> setCookies) {

        JsonNode json() {
            return JSON.readTree(body);
        }

        String setCookie() {
            return String.join("\n", setCookies);
        }
    }

    /** A browser stand-in that keeps the session cookie between requests. */
    private final class Client {

        private final HttpClient http = HttpClient.newHttpClient();
        private String sessionToken;

        Response get(String path) {
            return send(request(path).GET());
        }

        Response delete(String path) {
            return send(request(path).DELETE());
        }

        Response post(String path, Object body) {
            return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
        }

        Response upload(String kb, String filename, String markdown, boolean newVersion) {
            String boundary = "mindforge-" + UUID.randomUUID();
            String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/markdown\r\n\r\n" + markdown + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"newVersion\"\r\n\r\n" + newVersion + "\r\n"
                + "--" + boundary + "--\r\n";
            return send(request(kb + "/documents").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
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
                List<String> setCookies = new ArrayList<>(response.headers().allValues("Set-Cookie"));
                for (String cookie : setCookies) {
                    Matcher token = SESSION_COOKIE.matcher(cookie);
                    if (token.lookingAt()) {
                        sessionToken = token.group(1);
                    }
                }
                return new Response(response.statusCode(), response.body(), setCookies);
            } catch (Exception e) {
                throw new AssertionError("Request failed: " + e, e);
            }
        }
    }
}
