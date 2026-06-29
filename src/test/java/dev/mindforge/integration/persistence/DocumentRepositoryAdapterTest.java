package dev.mindforge.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DocumentStatus;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.KnowledgeBaseJpaRepository;
import dev.mindforge.infrastructure.persistence.UserJpaRepository;
import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import dev.mindforge.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class DocumentRepositoryAdapterTest extends TestContainerBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentRepository adapter;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private KnowledgeBaseJpaRepository knowledgeBaseJpaRepository;

    private UUID userId;
    private UUID kbId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
        userId = UUID.randomUUID();
        kbId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setDisplayName("Test User");
        user.setEmail("test-" + userId + "@example.com");
        userJpaRepository.save(user);

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setKbId(kbId);
        kb.setOwnerId(userId);
        kb.setName("Test KB");
        kb.setDocumentCount(0);
        knowledgeBaseJpaRepository.save(kb);
    }

    @Test
    void saveThenFindById_returnsPersistedDocument() {
        Document doc = makeDocument(null, kbId, "test-lesson", DocumentStatus.PENDING);

        Document saved = adapter.save(doc);

        Optional<Document> found = adapter.findById(saved.documentId());
        assertThat(found).isPresent();
        assertThat(found.get().documentId()).isEqualTo(saved.documentId());
        assertThat(found.get().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(found.get().lessonIdentity().lessonId()).isEqualTo("test-lesson");
    }

    @Test
    void findByContentHash_returnsDocumentWithMatchingHash() {
        Document doc = makeDocument(null, kbId, "hash-lesson", DocumentStatus.PENDING);
        Document saved = adapter.save(doc);

        Optional<Document> found = adapter.findByContentHash(saved.contentHash());
        assertThat(found).isPresent();
        assertThat(found.get().documentId()).isEqualTo(saved.documentId());
    }

    @Test
    void findByContentHash_returnsEmpty_whenNoMatch() {
        Optional<Document> found = adapter.findByContentHash(
            ContentHash.compute("nonexistent-content".getBytes()));
        assertThat(found).isEmpty();
    }

    @Test
    void updateStatus_changesDocumentStatus() {
        Document doc = makeDocument(null, kbId, "status-lesson", DocumentStatus.PENDING);
        Document saved = adapter.save(doc);

        adapter.updateStatus(saved.documentId(), DocumentStatus.DONE);

        Optional<Document> updated = adapter.findById(saved.documentId());
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(DocumentStatus.DONE);
    }

    @Test
    void listByKnowledgeBase_returnsAllDocumentsForKb() {
        adapter.save(makeDocument(null, kbId, "lesson-a", DocumentStatus.PENDING));
        adapter.save(makeDocument(null, kbId, "lesson-b", DocumentStatus.PENDING));

        List<Document> results = adapter.listByKnowledgeBase(kbId);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.knowledgeBaseId().equals(kbId));
    }

    @Test
    void findByContentHash_detectsDuplicateContent() {
        Document doc = makeDocument(null, kbId, "dedup-lesson", DocumentStatus.PENDING);
        Document saved = adapter.save(doc);

        Optional<Document> duplicate = adapter.findByContentHash(saved.contentHash());
        assertThat(duplicate).isPresent();
        assertThat(duplicate.get().documentId()).isEqualTo(saved.documentId());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Document makeDocument(UUID id, UUID kbId, String lessonId, DocumentStatus status) {
        Document base = TestFixtures.makeDocument(id, kbId, status);
        return new Document(
            base.documentId(), kbId,
            new LessonIdentity(lessonId, lessonId + " title"),
            ContentHash.compute((lessonId + "-unique-content").getBytes()),
            lessonId + ".md", "text/markdown", lessonId + " content",
            List.of(), UploadSource.API,
            userId, status,
            base.createdAt(), base.updatedAt()
        );
    }
}
