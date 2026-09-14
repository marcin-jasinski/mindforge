package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.application.service.DocumentUpload;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.parsing.MarkdownParser;
import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import dev.mindforge.infrastructure.persistence.jpa.KnowledgeBaseJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.UserJpaRepository;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class ConcurrentUploadTest extends TestContainerBase {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private KnowledgeBaseJpaRepository knowledgeBaseJpaRepository;

    @Test
    void twoConcurrentUploadsOfTheSameNewLesson_insertOneAndRejectTheOther() throws Exception {
        UUID userId = saveUser();
        UUID kbId = saveKnowledgeBase(userId);

        List<Object> outcomes = uploadConcurrently(
            makeUpload(kbId, userId, "# Mitoza\n\nPierwsza wersja notatek."),
            makeUpload(kbId, userId, "# Mitoza\n\nZupełnie inna wersja notatek."));

        assertThat(outcomes).filteredOn(UUID.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(LessonAlreadyExistsException.class::isInstance).hasSize(1);
        assertThat(documents.listByKnowledgeBase(kbId)).hasSize(1);
    }

    @Test
    void twoConcurrentIdenticalUploads_bothReturnTheOneInsertedDocument() throws Exception {
        UUID userId = saveUser();
        UUID kbId = saveKnowledgeBase(userId);
        DocumentUpload upload = makeUpload(kbId, userId, "# Mitoza\n\nTe same notatki.");

        List<Object> outcomes = uploadConcurrently(upload, upload);

        assertThat(outcomes).hasSize(2).allMatch(UUID.class::isInstance);
        assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertThat(documents.listByKnowledgeBase(kbId)).hasSize(1);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Starts every upload at once; each outcome is the document id or the lesson collision it was refused with. */
    private List<Object> uploadConcurrently(DocumentUpload... uploads) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = new ArrayList<>();
            for (DocumentUpload upload : uploads) {
                Callable<Object> task = () -> {
                    start.await();
                    try {
                        return ingestionService.ingest(upload);
                    } catch (LessonAlreadyExistsException collision) {
                        return collision;
                    }
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private static DocumentUpload makeUpload(UUID kbId, UUID userId, String markdown) {
        return new DocumentUpload(kbId, userId, UploadSource.API, "notatki.md", MarkdownParser.MIME_TYPE,
            markdown.getBytes(StandardCharsets.UTF_8), null, false);
    }

    private UUID saveUser() {
        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID());
        user.setDisplayName("Test User");
        user.setEmail("upload-" + user.getUserId() + "@example.com");
        return userJpaRepository.save(user).getUserId();
    }

    private UUID saveKnowledgeBase(UUID ownerId) {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setKbId(UUID.randomUUID());
        kb.setOwnerId(ownerId);
        kb.setName("Test KB");
        return knowledgeBaseJpaRepository.save(kb).getKbId();
    }
}
