package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.agent.ImageDescriber;
import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.RunFailure;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.export.BundleExporter;
import dev.mindforge.infrastructure.parsing.MarkdownParser;
import dev.mindforge.infrastructure.parsing.PdfParser;
import dev.mindforge.support.ExportedBundle;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class ImageIngestTest extends TestContainerBase {

    private static final String TEXT = "Mitosis is cell division.";
    private static final String DIAGRAM = "A diagram of one cell splitting into two daughter cells.";
    private static final String CHART = "A chart of the four phases of mitosis.";
    private static final String NOT_RELEVANT = "{\"relevant\": false, \"description\": \"\"}";
    private static final Pattern IMAGE_LINE = Pattern.compile("Image: [^\\n]*");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private IngestRunRepository runs;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private BundleExporter exporter;

    @Autowired
    private ProcessingSettings settings;

    @Autowired
    private StubAIGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aDecorativeImageIsDroppedAndARelevantOneReachesThePageInPlace() {
        UUID kbId = insertKnowledgeBase();
        givenAnswers(inOrder(NOT_RELEVANT, relevant(DIAGRAM)), 0, 2);

        IngestRun run = awaitRun(kbId, upload(kbId, makePdf(TEXT, Color.RED, Color.BLUE)), RunStatus.COMPLETED);

        List<StubAIGateway.Call> calls = gateway.recordedCalls();
        List<StubAIGateway.Call> describes = describeCalls();
        assertThat(describes).hasSize(2).allSatisfy(call -> {
            assertThat(call.tier()).isEqualTo(ModelTier.VISION);
            assertThat(call.image()).isNotNull();
            assertThat(call.prompt()).contains("Lesson title: cells").contains("<context>\n" + TEXT + "\n</context>");
        });
        assertThat(calls.indexOf(describes.getLast())).as("images are described before the guard")
            .isLessThan(indexOf(calls, Prompts.GUARD));
        assertThat(promptOf(Prompts.EXTRACT)).contains("[2] Image: " + DIAGRAM).doesNotContain("[1] Image");
        assertThat(run.failures()).containsExactly(RunFailure.ofItem("describe", "block 1", null, "not relevant"));
        assertThat(run.stepVersions()).containsEntry("ImageDescriber",
            settings.stepVersion(ImageDescriber.VERSION, ImageDescriber.TIER));
        assertThat(wiki.findByPath(kbId, "concepts/mitosis")).map(WikiPage::markdownBody)
            .hasValueSatisfying(body -> assertThat(body).contains("Image: " + DIAGRAM));
        Map<String, String> bundle = ExportedBundle.files(exporter, kbId, "Biology");
        for (StubAIGateway.Call describe : describes) {
            String base64 = Base64.getEncoder().encodeToString(describe.image().data());
            assertThat(bundle.values()).noneMatch(file -> file.contains(base64));
        }
        assertThat(bundle.values()).noneMatch(file -> file.contains("data:image"));
    }

    @Test
    void aBlankDescriptionDropsThatImageAndTheOtherReachesExtract() {
        UUID kbId = insertKnowledgeBase();
        givenAnswers(inOrder("", relevant(CHART)), 0, 2);

        IngestRun run = awaitRun(kbId, upload(kbId, makePdf(TEXT, Color.RED, Color.BLUE)), RunStatus.COMPLETED);

        assertThat(run.failures())
            .containsExactly(RunFailure.ofItem("describe", "block 1", null, "no usable description"));
        assertThat(promptOf(Prompts.EXTRACT)).contains("[2] Image: " + CHART);
    }

    @Test
    void anUnavailableGatewayFailsTheRunRetryableWithoutADrop() {
        UUID kbId = insertKnowledgeBase();
        AIGatewayUnavailableException unavailable = new AIGatewayUnavailableException(new IllegalStateException());
        givenAnswers(prompt -> {
            throw unavailable;
        }, 0, 1);

        IngestRun run = awaitRun(kbId, upload(kbId, makePdf(TEXT, Color.RED)), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly(unavailable.getMessage(), true);
        assertThat(run.failures()).noneMatch(failure -> failure.step().equals("describe"));
        assertThat(gateway.recordedCalls()).noneMatch(call -> call.prompt().contains(Prompts.GUARD));
    }

    @Test
    void anExceededDeadlineFailsTheRunRetryable() {
        UUID kbId = insertKnowledgeBase();
        DeadlineExceededException late =
            new DeadlineExceededException(DeadlineProfile.BACKGROUND, Duration.ofSeconds(1));
        givenAnswers(prompt -> {
            throw late;
        }, 0, 1);

        IngestRun run = awaitRun(kbId, upload(kbId, makePdf(TEXT, Color.RED)), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly(late.getMessage(), true);
        assertThat(run.failures()).noneMatch(failure -> failure.step().equals("describe"));
    }

    @Test
    void anImageOnlyDocumentWithNoRelevantImageFailsRetryableAndARetryDescribesAgain() {
        UUID kbId = insertKnowledgeBase();
        givenAnswers(prompt -> NOT_RELEVANT, 0, 1);
        UUID documentId = upload(kbId, makePdf(null, Color.RED, Color.BLUE));

        IngestRun failed = awaitRun(kbId, documentId, RunStatus.FAILED);

        assertThat(failed).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("no image could be described", true);
        assertThat(failed.failures()).containsExactly(
            RunFailure.ofItem("describe", "block 0", null, "not relevant"),
            RunFailure.ofItem("describe", "block 1", null, "not relevant"));
        assertThat(failed.stepVersions()).containsKey("ImageDescriber");

        givenAnswers(inOrder(relevant(DIAGRAM), relevant(CHART)), 0, 1);
        ingestion.retry(kbId, documentId);
        IngestRun retried = awaitRun(kbId, documentId, RunStatus.COMPLETED);

        assertThat(describeCalls()).hasSize(2).allSatisfy(call -> assertThat(call.image()).isNotNull());
        assertThat(promptOf(Prompts.EXTRACT)).contains("[0] Image: " + DIAGRAM).contains("[1] Image: " + CHART);
        assertThat(retried.failures()).noneMatch(failure -> failure.step().equals("describe"));
    }

    @Test
    void anImageOnlyDocumentWithOnlyBlankDescriptionsFailsRetryable() {
        UUID kbId = insertKnowledgeBase();
        givenAnswers(prompt -> "", 0, 0);

        IngestRun run = awaitRun(kbId, upload(kbId, makePdf(null, Color.RED)), RunStatus.FAILED);

        assertThat(run).extracting(IngestRun::failureReason, IngestRun::retryable)
            .containsExactly("no image could be described", true);
        assertThat(run.failures())
            .containsExactly(RunFailure.ofItem("describe", "block 0", null, "no usable description"));
    }

    @Test
    void aDocumentWithTextCompletesOnItsTextWhenEveryImageIsDropped() {
        UUID kbId = insertKnowledgeBase();
        givenAnswers(prompt -> "", 0, 0);

        IngestRun run = awaitRun(kbId, upload(kbId, makePdf(TEXT, Color.RED, Color.BLUE)), RunStatus.COMPLETED);

        assertThat(run.failures()).containsExactly(
            RunFailure.ofItem("describe", "block 1", null, "no usable description"),
            RunFailure.ofItem("describe", "block 2", null, "no usable description"));
        assertThat(promptOf(Prompts.EXTRACT)).contains("[0] " + TEXT).doesNotContain("Image:");
        assertThat(run.stepVersions()).containsKey("ImageDescriber");
    }

    @Test
    void aDocumentWithoutImagesMakesNoVisionCall() {
        UUID kbId = insertKnowledgeBase();
        givenAnswers(prompt -> {
            throw new AssertionError("no image to describe");
        }, 0, 0);

        IngestRun run = awaitRun(kbId, ingestion.ingest(new DocumentUpload(kbId, ownerOf(kbId), UploadSource.API,
            "cells.md", MarkdownParser.MIME_TYPE, (TEXT + "\n").getBytes(StandardCharsets.UTF_8), null, false)),
            RunStatus.COMPLETED);

        assertThat(gateway.recordedCalls()).noneMatch(call -> call.tier() == ModelTier.VISION);
        assertThat(run.stepVersions()).doesNotContainKey("ImageDescriber");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Every page cites blocks {@code first..last}; a page body repeats the image line its Write prompt holds. */
    private void givenAnswers(UnaryOperator<String> describe, int first, int last) {
        gateway.reset();
        gateway.answer(Prompts.DESCRIBE, describe);
        gateway.answer(Prompts.GUARD, Prompts.RELEVANT);
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("text", "Mitosis splits a cell in two.");
        claim.put("title", "Mitosis");
        claim.put("targetPath", null);
        claim.put("firstBlock", first);
        claim.put("lastBlock", last);
        gateway.answer(Prompts.EXTRACT,
            Prompts.json(Map.of("claims", List.of(claim), "chunkDigest", "Mitosis splits a cell.")));
        gateway.answer(Prompts.WRITE, prompt -> {
            Matcher image = IMAGE_LINE.matcher(prompt);
            String body = "# Phases\n\n" + (image.find() ? image.group() : "Cell division.") + "\n";
            return Prompts.json(Map.of("description", "Cell division.", "body", body));
        });
        gateway.answer(Prompts.LINKS, "{\"insertions\": []}");
        gateway.answer(Prompts.SUPERSEDE, "{\"proposals\": []}");
    }

    /** Answers the n-th call with the n-th response; images are described one by one in document order. */
    private static UnaryOperator<String> inOrder(String... responses) {
        AtomicInteger next = new AtomicInteger();
        return prompt -> responses[next.getAndIncrement()];
    }

    private static String relevant(String description) {
        return Prompts.json(Map.of("relevant", true, "description", description));
    }

    private List<StubAIGateway.Call> describeCalls() {
        return gateway.recordedCalls().stream().filter(call -> call.prompt().contains(Prompts.DESCRIBE)).toList();
    }

    private String promptOf(String fragment) {
        return gateway.recordedCalls().stream().map(StubAIGateway.Call::prompt)
            .filter(prompt -> prompt.contains(fragment)).findFirst().orElseThrow();
    }

    private static int indexOf(List<StubAIGateway.Call> calls, String fragment) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).prompt().contains(fragment)) {
                return i;
            }
        }
        throw new AssertionError("No call with " + fragment);
    }

    private UUID upload(UUID kbId, byte[] pdf) {
        return ingestion.ingest(new DocumentUpload(kbId, ownerOf(kbId), UploadSource.API, "cells.pdf",
            PdfParser.MIME_TYPE, pdf, null, false));
    }

    /** One page: the text (if any), then one 80 px square image per colour, drawn in order. */
    private static byte[] makePdf(String text, Color... colors) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                if (text != null) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(text);
                    stream.endText();
                }
                int x = 72;
                for (Color color : colors) {
                    PDImageXObject image = LosslessFactory.createFromImage(pdf, makeImage(color));
                    stream.drawImage(image, x, 500, 80, 80);
                    x += 100;
                }
            }
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage makeImage(Color color) {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 80, 80);
        graphics.dispose();
        return image;
    }

    private IngestRun awaitRun(UUID kbId, UUID documentId, RunStatus status) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            Optional<IngestRun> run = runs.latestForDocument(kbId, documentId);
            if (run.isPresent() && Set.of(RunStatus.COMPLETED, RunStatus.FAILED).contains(run.get().status())) {
                assertThat(run.get().status()).as("run failed with %s", run.get().failureReason()).isEqualTo(status);
                return run.get();
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Run of " + documentId + " is still " + run.map(IngestRun::status));
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private UUID ownerOf(UUID kbId) {
        return jdbc.queryForObject("SELECT owner_id FROM knowledge_bases WHERE kb_id = ?", UUID.class, kbId);
    }

    private UUID insertKnowledgeBase() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "image-" + userId + "@example.com");
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, userId);
        return kbId;
    }
}
