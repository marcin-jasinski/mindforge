package dev.mindforge.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    "spring.jpa.hibernate.ddl-auto=none",
    "springdoc.api-docs.enabled=true"
})
class ApiFlowTest extends TestContainerBase {

    private static final Pattern FORBIDDEN_FIELDS = Pattern.compile(
        "\"(cost|stepVersions|step_versions|passwordHash|referenceAnswer|groundingContext|rawPrompt|rawCompletion|token|groundingExcerpt)\"");
    private static final Pattern SESSION_COOKIE = Pattern.compile("token=([^;]*)");
    private static final String NOTES = "# Mitosis\n\nMitosis is cell division.\n";
    private static final String BODY = "# Course\n\nMitosis splits a cell in two.\n";
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
        String email = "alice-" + UUID.randomUUID() + "@example.com";

        Response registered = client.post("/api/v1/auth/register",
            Map.of("displayName", "Alice", "email", email, "password", "secret-password"));

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.setCookie()).contains("token=").contains("HttpOnly").contains("SameSite=Lax");
        assertNoSensitiveFields(registered);
        assertThat(client.get("/api/v1/auth/me").json().get("email").asString()).isEqualTo(email);
        assertThat(client.post("/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);
        assertThat(client.get("/api/v1/auth/me").status()).isEqualTo(401);

        Client other = new Client();
        assertThat(other.post("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password")).status())
            .isEqualTo(401);
        assertThat(other.post("/api/v1/auth/login", Map.of("email", email.toUpperCase(), "password", "secret-password"))
            .status()).isEqualTo(200);
        assertThat(other.get("/api/v1/auth/me").status()).isEqualTo(200);
    }

    // ---------------------------------------------------------------------------
    // Upload and the wiki
    // ---------------------------------------------------------------------------

    @Test
    void anUploadIngestsIntoPagesAndNoResponseCarriesASensitiveField() {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        givenAnswers();

        Response upload = client.upload(kb, "biology.md", NOTES, false);

        assertThat(upload.status()).isEqualTo(202);
        String documentId = upload.json().get("documentId").asString();
        awaitRun(client, kb, documentId, "COMPLETED");

        Response index = client.get(kb + "/index");
        assertThat(index.json().get("pages").findValuesAsString("path"))
            .containsExactlyInAnyOrder("concepts/mitosis", "sources/biology");
        Response page = client.get(kb + "/pages/concepts/mitosis");
        assertThat(page.json().get("markdown").asString())
            .startsWith(BODY).contains("# Citations\n\n[1] [biology](/sources/biology.md)");
        Response runs = client.get(kb + "/runs");
        String runId = runs.json().get(0).get("runId").asString();
        Response report = client.get(kb + "/runs/" + runId);
        assertThat(report.json().get("revertOffered").asBoolean()).isTrue();
        assertThat(report.json().get("pages")).hasSize(2);

        List<Response> responses = List.of(upload, index, page, runs, report, client.get(kb),
            client.get(kb + "/documents"), client.get(kb + "/documents/" + documentId), client.get(kb + "/graph"),
            client.get(kb + "/health"), client.get(kb + "/pages/concepts/mitosis/revisions"), client.get("/api/v1/auth/me"));
        responses.forEach(response -> assertThat(response.status()).as(response.body()).isLessThan(300));
        responses.forEach(ApiFlowTest::assertNoSensitiveFields);

        Response reverted = client.post(kb + "/runs/" + runId + "/revert", Map.of());
        assertThat(reverted.status()).isEqualTo(200);
        assertThat(client.get(kb + "/index").json().get("pages")).isEmpty();
        assertThat(client.post(kb + "/runs/" + runId + "/revert", Map.of()).json().get("code").asString())
            .isEqualTo("REVERT_NOT_ALLOWED");
    }

    @Test
    void theOwnerDownloadsTheBundleAsAZipNamedAfterTheKnowledgeBase() throws Exception {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        givenAnswers();
        awaitRun(client, kb, client.upload(kb, "biology.md", NOTES, false).json().get("documentId").asString(),
            "COMPLETED");

        HttpResponse<byte[]> export = client.download(kb + "/export");

        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.headers().firstValue("Content-Type")).contains("application/zip");
        assertThat(export.headers().firstValue("Content-Disposition"))
            .contains("attachment; filename=\"biology-okf.zip\"");
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(export.body()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).containsExactly("biology-okf/index.md", "biology-okf/log.md",
            "biology-okf/concepts/mitosis.md", "biology-okf/sources/biology.md");
        assertThat(signedUp().download(kb + "/export").statusCode()).isEqualTo(403);
    }

    @Test
    void aLearnerStudiesFlashcardsAndAQuizWithoutEverSeeingAReferenceAnswer() {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        givenAnswers();
        gateway.answer(Prompts.FLASHCARDS, "{\"cards\": [{\"type\": \"BASIC\", \"front\": \"What is mitosis?\","
            + " \"back\": \"Cell division.\", \"section\": \"course\"}]}");
        gateway.answer(Prompts.QUIZ, Prompts.json(Map.of("questions", List.of(Map.of("pagePath", "concepts/mitosis",
            "question", "What does mitosis mean?", "referenceAnswer", "SECRET-REFERENCE-ANSWER",
            "groundingExcerpt", "Mitosis splits a cell in two.")))));
        gateway.answer(Prompts.GRADE, "{\"score\": 4, \"feedback\": \"Prawie dobrze.\"}");
        String documentId = client.upload(kb, "biology.md", NOTES, false).json().get("documentId").asString();
        awaitRun(client, kb, documentId, "COMPLETED");

        assertThat(client.get(kb + "/flashcards").json()).as("listing a deck generates nothing").isEmpty();
        Response deck = client.post(kb + "/flashcards/refresh", Map.of());
        assertThat(deck.json()).hasSize(1);
        assertThat(client.get(kb + "/flashcards").json()).hasSize(1);
        String cardId = deck.json().get(0).get("cardId").asString();
        Response reviewed = client.post(kb + "/flashcards/" + cardId + "/reviews", Map.of("rating", 5));
        assertThat(Instant.parse(reviewed.json().get("dueAt").asString())).isAfter(Instant.now());
        assertThat(client.post(kb + "/flashcards/" + cardId + "/reviews", Map.of("rating", 9)).status()).isEqualTo(400);

        Response started = client.post(kb + "/quiz-sessions", Map.of());
        assertThat(started.status()).as("the questions are written after the response").isEqualTo(202);
        String session = kb + "/quiz-sessions/" + started.json().get("sessionId").asString();
        Response next = nextQuestion(client, session);
        assertThat(next.json().get("question").asString()).isEqualTo("What does mitosis mean?");
        Response graded = client.post(session + "/answers", Map.of("answer", "Division."));
        assertThat(graded.json().get("score").asInt()).isEqualTo(4);
        assertThat(graded.json().get("finished").asBoolean()).isTrue();
        assertThat(client.get(session + "/next").status()).isEqualTo(204);
        assertThat(client.post(session + "/answers", Map.of("answer", "Again.")).json().get("code").asString())
            .isEqualTo("QUIZ_FINISHED");

        for (Response response : List.of(deck, reviewed, started, next, graded)) {
            assertNoSensitiveFields(response);
            assertThat(response.body()).doesNotContain("SECRET-REFERENCE-ANSWER");
        }
        Client other = signedUp();
        assertThat(other.get(kb + "/flashcards").status()).isEqualTo(403);
        assertThat(other.post(kb + "/flashcards/refresh", Map.of()).status()).isEqualTo(403);
        assertThat(other.delete(kb + "/documents/" + documentId).status()).isEqualTo(403);
        assertThat(client.delete(kb + "/documents/" + documentId).status())
            .as("a document that wrote pages is not removable").isEqualTo(409);
        assertThat(other.get(session + "/next").status()).isEqualTo(403);
    }

    @Test
    void aLearnerAsksTheWikiAndEditsItFromChat() {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        givenAnswers();
        gateway.answer(Prompts.SELECT, "{\"paths\": [\"concepts/mitosis\", \"concepts/missing\"]}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Mitosis splits a cell.\", \"citations\": [\"concepts/mitosis\"]}");
        gateway.answer(Prompts.EDIT, "{\"items\": [{\"kind\": \"retitle\", \"path\": \"concepts/mitosis\","
            + " \"title\": \"Cell mitosis\"}]}");
        awaitRun(client, kb, client.upload(kb, "biology.md", NOTES, false).json().get("documentId").asString(),
            "COMPLETED");

        Response session = client.post(kb + "/query-sessions", Map.of());
        assertThat(session.status()).isEqualTo(201);
        String chat = kb + "/query-sessions/" + session.json().get("interactionId").asString();
        Response answer = client.post(chat + "/messages", Map.of("question", "What is mitosis?"));
        assertThat(answer.json().get("answer").asString()).isEqualTo("Mitosis splits a cell.");
        assertThat(answer.body()).contains("\"citedPaths\":[\"concepts/mitosis\"]").doesNotContain("usedPagePaths");
        Response history = client.get(kb + "/query-sessions");
        assertThat(history.json()).hasSize(1);
        assertThat(history.body()).doesNotContain("usedPagePaths").doesNotContain("concepts/mitosis");
        Response search = client.get(kb + "/pages/search?q=mito");
        assertThat(search.json().findValuesAsString("path")).containsExactly("concepts/mitosis");

        Response edit = client.post(chat + "/edits", Map.of("instruction", "Give mitosis a fuller name."));
        assertThat(edit.status()).isEqualTo(202);
        String runId = edit.json().get("runId").asString();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!"COMPLETED".equals(client.get(kb + "/runs/" + runId).json().get("status").asString())) {
            assertThat(System.nanoTime()).as("edit run did not complete").isLessThan(deadline);
            await(Duration.ofMillis(100));
        }
        assertThat(client.get(kb + "/pages/concepts/mitosis").json().get("title").asString()).isEqualTo("Cell mitosis");

        for (Response response : List.of(session, answer, history, search, edit)) {
            assertNoSensitiveFields(response);
        }
        Client other = signedUp();
        assertThat(other.post(chat + "/messages", Map.of("question", "Someone else's?")).status()).isEqualTo(403);
        assertThat(client.get(kb + "/flashcards?lessonId=conversation").status()).isEqualTo(404);
    }

    @Test
    void unreadableModelOutputIsAServiceUnavailableNotAServerError() {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        gateway.reset();
        gateway.answer(Prompts.SELECT, "{\"paths\": []}");
        gateway.answer(Prompts.ANSWER, "this is not JSON");
        String chat = kb + "/query-sessions/"
            + client.post(kb + "/query-sessions", Map.of()).json().get("interactionId").asString();

        Response answer = client.post(chat + "/messages", Map.of("question", "What is mitosis?"));
        gateway.reset(); // stubs are first-match and shared across tests

        assertThat(answer.status()).isEqualTo(503);
        assertThat(answer.json().get("code").asString()).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(answer.json().get("detail").asString()).isEqualTo("The model returned unreadable output");
    }

    @Test
    void aMalformedBodyIsAnsweredWithAFixedMessageThatEchoesNothing() {
        Response response = new Client().postRaw("/api/v1/auth/login", "{\"email\": \"secret-snippet\", ");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("detail").asString()).isEqualTo("Malformed request body");
        assertThat(response.body()).doesNotContain("secret-snippet").doesNotContain("dev.mindforge");
    }

    @Test
    void onlyTheActuatorHealthProbeIsPublic() {
        Client client = new Client();

        assertThat(client.get("/actuator/health").status()).isEqualTo(200);
        assertThat(client.get("/actuator/env").status()).isIn(401, 403);
        assertThat(client.get("/health").status()).isEqualTo(404);
    }

    /** The question once the quiz's questions are written; a 202 means they are still being written. */
    private Response nextQuestion(Client client, String session) {
        Response next = client.get(session + "/next");
        for (int attempt = 0; attempt < 200 && next.status() == 202; attempt++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            next = client.get(session + "/next");
        }
        return next;
    }

    @Test
    void theSignInPageIsToldWhichProvidersAreConfigured() {
        Response providers = new Client().get("/api/v1/auth/providers");

        assertThat(providers.status()).isEqualTo(200);
        assertThat(providers.json().get("providers").isArray()).isTrue();
        assertThat(providers.body()).doesNotContain("not-configured").doesNotContain("secret");
    }

    @Test
    void theLoginRouteIsTheSinglePageApp() {
        Response login = new Client().get("/login");

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body()).contains("<app-root>").doesNotContain("Please sign in");
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
        assertThat(client.upload(kb, "biology.md", NOTES, false).status()).isEqualTo(202);

        Response collision = client.upload(kb, "biology.md", NOTES + "\nRevision.\n", false);

        assertThat(collision.status()).isEqualTo(409);
        assertThat(collision.json().get("code").asString()).isEqualTo("LESSON_EXISTS");
        assertThat(collision.json().get("lessonId").asString()).isEqualTo("biology");
        assertThat(collision.json().get("lessonTitle").asString()).isEqualTo("biology");
        assertThat(client.upload(kb, "biology.md", NOTES + "\nRevision.\n", true).status()).isEqualTo(202);
    }

    // ---------------------------------------------------------------------------
    // Queue, retry and deletion
    // ---------------------------------------------------------------------------

    @Test
    void aBusyKnowledgeBaseCannotBeDeletedWhileAQueuedUploadAndARetryRunInOrder() throws Exception {
        Client client = signedUp();
        String kb = createKnowledgeBase(client);
        gateway.reset();
        gateway.answer("Shop receipt", "{\"relevant\": false, \"reason\": \"It is a receipt.\", \"confidence\": 0.9}");
        givenAnswers();
        String receipt = client.upload(kb, "receipt.md", "Shop receipt.\n", false).json().get("documentId").asString();
        awaitRun(client, kb, receipt, "FAILED");

        CountDownLatch extracting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        gateway.reset();
        gateway.answer("] First lesson", prompt -> {
            extracting.countDown();
            await(release);
            return extraction();
        });
        givenAnswers();
        String first = client.upload(kb, "lesson-1.md", "First lesson about cells.\n", false).json()
            .get("documentId").asString();
        assertThat(extracting.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        String queued = client.upload(kb, "lesson-2.md", "Second lesson about cells.\n", false).json()
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

        for (String path : List.of(kb, kb + "/index", kb + "/pages/concepts/mitosis", kb + "/runs", kb + "/progress",
            kb + "/health", kb + "/documents", kb + "/graph")) {
            assertThat(other.get(path).status()).as(path).isEqualTo(403);
        }
        assertThat(other.delete(kb).status()).isEqualTo(403);
        assertThat(other.upload(kb, "biology.md", NOTES, false).status()).isEqualTo(403);
        assertThat(other.post(kb + "/lint-runs", Map.of()).status()).isEqualTo(403);
        assertThat(new Client().get(kb + "/index").status()).isEqualTo(401);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void givenAnswers() {
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        gateway.answer(Prompts.EXTRACT, extraction());
        gateway.answer(Prompts.WRITE, Prompts.json(Map.of("description", "Cell division.", "body", BODY)));
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    private static String extraction() {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("text", "Mitosis is cell division.");
        claim.put("title", "Mitosis");
        claim.put("targetPath", null);
        claim.put("firstBlock", 0);
        claim.put("lastBlock", 1);
        return Prompts.json(Map.of("claims", List.of(claim), "chunkDigest", "Mitosis."));
    }

    private Client signedUp() {
        Client client = new Client();
        Response registered = client.post("/api/v1/auth/register", Map.of("displayName", "Student",
            "email", "student-" + UUID.randomUUID() + "@example.com", "password", "secret-password"));
        assertThat(registered.status()).isEqualTo(201);
        return client;
    }

    private static String createKnowledgeBase(Client client) {
        Response created = client.post("/api/v1/knowledge-bases", Map.of("name", "Biology", "description", "Notes"));
        assertThat(created.status()).isEqualTo(201);
        return "/api/v1/knowledge-bases/" + created.json().get("kbId").asString();
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

        Response postRaw(String path, String body) {
            return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
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
