package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.mindforge.support.Prompts;

/**
 * The E2E stack's WireMock mappings (ADR-0042) match prompts by their {@code # Task: ...} heading; this catches a
 * heading renamed in a prompt resource and an answer the openai client cannot read, before the E2E runs.
 */
class ModelStubMappingsTest {

    private static final Path MAPPINGS = Path.of("src/test/e2e/model-stub/mappings");
    private static final Path PROMPTS = Path.of("src/main/resources/prompts");
    private static final String CATCH_ALL = "zz-unmatched.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void shouldHaveNoProblemInTheCommittedMappings() throws IOException {
        assertThat(problems(MAPPINGS, PROMPTS)).isEmpty();
    }

    @Test
    void shouldMapEveryPromptOfTheJourneyAndNameItsPromptsConstant() throws IOException {
        List<String> fragments = new ArrayList<>();
        List<String> constants = new ArrayList<>();
        for (Path mapping : mappings(MAPPINGS)) {
            if (!isCatchAll(mapping)) {
                JsonNode root = JSON.readTree(Files.readString(mapping, StandardCharsets.UTF_8));
                fragments.add(fragment(root));
                constants.add(root.path("metadata").path("prompt").asString());
            }
        }

        assertThat(fragments).containsExactlyInAnyOrder(Prompts.GUARD, Prompts.EXTRACT, Prompts.WRITE, Prompts.LINKS,
            Prompts.SUPERSEDE, Prompts.FLASHCARDS, Prompts.QUIZ, Prompts.GRADE);
        assertThat(constants).containsExactlyInAnyOrder("Prompts.GUARD", "Prompts.EXTRACT", "Prompts.WRITE",
            "Prompts.LINKS", "Prompts.SUPERSEDE", "Prompts.FLASHCARDS", "Prompts.QUIZ", "Prompts.GRADE");
    }

    @Test
    void shouldAnswerAnUnknownPromptWithA400AtTheLowestPriority() throws IOException {
        JsonNode root = JSON.readTree(Files.readString(MAPPINGS.resolve(CATCH_ALL), StandardCharsets.UTF_8));

        assertThat(root.path("priority").asInt()).isEqualTo(10);
        assertThat(root.path("request").path("method").asString()).isEqualTo("POST");
        assertThat(root.path("response").path("status").asInt()).isEqualTo(400);
        assertThat(root.path("response").path("body").asString()).isEqualTo("no e2e stub answer for this prompt");
    }

    @Test
    void shouldKeepTheQuizReferenceAnswerAMarkerTheJourneyCanLookFor() throws IOException {
        JsonNode quiz = JSON.readTree(content(JSON.readTree(
            Files.readString(MAPPINGS.resolve("quiz.json"), StandardCharsets.UTF_8))));

        assertThat(quiz.path("questions").get(0).path("referenceAnswer").asString())
            .isEqualTo("E2E-SECRET-REFERENCE-ANSWER");
    }

    @Test
    void shouldReportAFragmentThatNoPromptResourceContains(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("renamed.json"), mapping("# Task: a heading no prompt has", "{}"));

        assertThat(problems(dir, PROMPTS)).singleElement().asString()
            .contains("renamed.json").contains("in no prompt resource");
    }

    @Test
    void shouldReportAResponseThatIsNotAChatCompletion(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("no-choices.json"), """
            {"request": {"method": "POST", "urlPath": "/v1/chat/completions",
                         "bodyPatterns": [{"contains": "%s"}]},
             "response": {"status": 200, "jsonBody": {"object": "chat.completion"}}}
            """.formatted(Prompts.GUARD));

        assertThat(problems(dir, PROMPTS)).singleElement().asString()
            .contains("no-choices.json").contains("not a chat completion");
    }

    @Test
    void shouldReportAContentThatIsNotJson(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("prose.json"), mapping(Prompts.GUARD, "relevant, I think"));

        assertThat(problems(dir, PROMPTS)).singleElement().asString()
            .contains("prose.json").contains("content is not JSON");
    }

    private static List<String> problems(Path mappingDir, Path promptDir) throws IOException {
        List<String> prompts = new ArrayList<>();
        try (Stream<Path> files = Files.walk(promptDir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                prompts.add(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        List<String> problems = new ArrayList<>();
        for (Path mapping : mappings(mappingDir)) {
            if (isCatchAll(mapping)) {
                continue;
            }
            String name = mapping.getFileName().toString();
            JsonNode root = JSON.readTree(Files.readString(mapping, StandardCharsets.UTF_8));
            String fragment = fragment(root);
            if (fragment.isEmpty() || prompts.stream().noneMatch(prompt -> prompt.contains(fragment))) {
                problems.add(name + ": fragment '" + fragment + "' is in no prompt resource");
            }
            if (!isChatCompletion(root.path("response"))) {
                problems.add(name + ": response is not a chat completion");
            } else if (!isJson(content(root))) {
                problems.add(name + ": content is not JSON");
            }
        }
        return problems;
    }

    private static List<Path> mappings(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    private static boolean isCatchAll(Path mapping) {
        return mapping.getFileName().toString().equals(CATCH_ALL);
    }

    private static String fragment(JsonNode root) {
        return root.path("request").path("bodyPatterns").path(0).path("contains").asString("");
    }

    private static boolean isChatCompletion(JsonNode response) {
        JsonNode body = response.path("jsonBody");
        JsonNode message = body.path("choices").path(0).path("message");
        return response.path("status").asInt() == 200
            && body.path("id").isString()
            && "chat.completion".equals(body.path("object").asString(""))
            && body.path("created").isNumber()
            && body.path("model").isString()
            && "assistant".equals(message.path("role").asString(""))
            && message.path("content").isString()
            && body.path("choices").path(0).path("finish_reason").isString()
            && body.path("usage").path("total_tokens").isNumber();
    }

    private static String content(JsonNode root) {
        return root.path("response").path("jsonBody").path("choices").path(0).path("message").path("content")
            .asString();
    }

    private static boolean isJson(String content) {
        try {
            return JSON.readTree(content).isObject();
        } catch (JacksonException e) {
            return false;
        }
    }

    private static String mapping(String fragment, String content) {
        return """
            {"request": {"method": "POST", "urlPath": "/v1/chat/completions", "bodyPatterns": [{"contains": %s}]},
             "response": {"status": 200, "jsonBody": {"id": "chatcmpl-test", "object": "chat.completion",
               "created": 1, "model": "e2e-stub",
               "choices": [{"index": 0, "message": {"role": "assistant", "content": %s}, "finish_reason": "stop"}],
               "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}}}
            """.formatted(JSON.writeValueAsString(fragment), JSON.writeValueAsString(content));
    }
}
