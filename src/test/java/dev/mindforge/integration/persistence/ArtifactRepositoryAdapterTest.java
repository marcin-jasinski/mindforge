package dev.mindforge.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.ConceptMapData;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DocumentArtifact;
import dev.mindforge.domain.model.DocumentStatus;
import dev.mindforge.domain.model.FlashcardData;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.StepCheckpoint;
import dev.mindforge.domain.model.SummaryData;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.ValidationResult;
import dev.mindforge.domain.port.ArtifactRepository;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.jpa.KnowledgeBaseJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.UserJpaRepository;
import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import dev.mindforge.support.TestContainerBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class ArtifactRepositoryAdapterTest extends TestContainerBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private KnowledgeBaseJpaRepository knowledgeBaseJpaRepository;

    private UUID userId;
    private UUID kbId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
        userId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setDisplayName("Test User");
        user.setEmail("artifact-test-" + userId + "@example.com");
        userJpaRepository.save(user);

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setKbId(kbId);
        kb.setOwnerId(userId);
        kb.setName("Artifact Test KB");
        kb.setDocumentCount(0);
        knowledgeBaseJpaRepository.save(kb);

        Document doc = new Document(
            documentId, kbId,
            new LessonIdentity("artifact-lesson", "Artifact Lesson"),
            dev.mindforge.domain.model.ContentHash.compute("artifact-content".getBytes()),
            "artifact.md", "text/markdown", "artifact content",
            List.of(), UploadSource.API,
            userId, DocumentStatus.PROCESSING,
            Instant.now(), Instant.now()
        );
        documentRepository.save(doc);
    }

    @Test
    void saveCheckpoint_thenLoadLatest_roundTripsArtifact() {
        DocumentArtifact artifact = makeArtifact(documentId, kbId);

        artifactRepository.saveCheckpoint(artifact);

        Optional<DocumentArtifact> loaded = artifactRepository.loadLatest(documentId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().documentId()).isEqualTo(documentId);
        assertThat(loaded.get().summary().summary()).isEqualTo("Test summary");
        assertThat(loaded.get().summary().keyPoints()).containsExactly("point one", "point two");
        assertThat(loaded.get().flashcards()).hasSize(1);
        assertThat(loaded.get().flashcards().get(0).front()).isEqualTo("What is JPA?");
    }

    @Test
    void saveCheckpoint_persistsStepFingerprints() {
        StepCheckpoint checkpoint = new StepCheckpoint("preprocessor", "abc123", Instant.now());
        DocumentArtifact artifact = new DocumentArtifact(
            UUID.randomUUID(), documentId, kbId,
            null, List.of(), null, List.of(), null,
            Map.of("preprocessor", checkpoint),
            "preprocessor", Instant.now()
        );

        artifactRepository.saveCheckpoint(artifact);

        Optional<DocumentArtifact> loaded = artifactRepository.loadLatest(documentId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().stepFingerprints()).containsKey("preprocessor");
        assertThat(loaded.get().stepFingerprints().get("preprocessor").fingerprint())
            .isEqualTo("abc123");
    }

    @Test
    void saveCheckpoint_replacesExistingCheckpoints_onSubsequentSave() {
        StepCheckpoint v1 = new StepCheckpoint("preprocessor", "fingerprint-v1", Instant.now());
        DocumentArtifact first = new DocumentArtifact(
            UUID.randomUUID(), documentId, kbId,
            null, List.of(), null, List.of(), null,
            Map.of("preprocessor", v1), "preprocessor", Instant.now()
        );
        artifactRepository.saveCheckpoint(first);

        StepCheckpoint v2 = new StepCheckpoint("preprocessor", "fingerprint-v2", Instant.now());
        DocumentArtifact second = new DocumentArtifact(
            first.artifactId(), documentId, kbId,
            null, List.of(), null, List.of(), null,
            Map.of("preprocessor", v2), "preprocessor", Instant.now()
        );
        artifactRepository.saveCheckpoint(second);

        Optional<DocumentArtifact> loaded = artifactRepository.loadLatest(documentId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().stepFingerprints().get("preprocessor").fingerprint())
            .isEqualTo("fingerprint-v2");
    }

    @Test
    void loadLatest_returnsEmpty_whenNoArtifact() {
        Optional<DocumentArtifact> loaded = artifactRepository.loadLatest(UUID.randomUUID());
        assertThat(loaded).isEmpty();
    }

    @Test
    void countFlashcards_returnsCorrectTotal() {
        FlashcardData card1 = FlashcardData.create(kbId, "lesson-a", CardType.BASIC, "Q1", "A1");
        FlashcardData card2 = FlashcardData.create(kbId, "lesson-a", CardType.BASIC, "Q2", "A2");

        DocumentArtifact artifact = new DocumentArtifact(
            UUID.randomUUID(), documentId, kbId,
            null, List.of(card1, card2), null, List.of(), null,
            Map.of(), null, Instant.now()
        );
        artifactRepository.saveCheckpoint(artifact);

        long count = artifactRepository.countFlashcards(kbId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void countFlashcards_returnsZero_whenNoArtifacts() {
        long count = artifactRepository.countFlashcards(UUID.randomUUID());
        assertThat(count).isZero();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static DocumentArtifact makeArtifact(UUID documentId, UUID kbId) {
        SummaryData summary = new SummaryData("Test summary", List.of("point one", "point two"));
        FlashcardData card = FlashcardData.create(kbId, "artifact-lesson", CardType.BASIC,
            "What is JPA?", "Java Persistence API");
        ConceptMapData conceptMap = new ConceptMapData(
            List.of(new ConceptMapData.ConceptNode("jpa", "JPA")),
            List.of()
        );
        ValidationResult validation = new ValidationResult(true, "relevant", 0.95f);
        StepCheckpoint checkpoint = new StepCheckpoint("summarizer", "fp12345678901234", Instant.now());

        return new DocumentArtifact(
            UUID.randomUUID(), documentId, kbId,
            summary, List.of(card), conceptMap, List.of("Quiz question?"),
            validation, Map.of("summarizer", checkpoint),
            "summarizer", Instant.now()
        );
    }
}
