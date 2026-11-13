package dev.mindforge.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.ImageInput;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.jpa.KnowledgeBaseJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.UserJpaRepository;
import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import dev.mindforge.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setDisplayName("Test User");
        user.setEmail("test-" + userId + "@example.com");
        userJpaRepository.save(user);

        kbId = saveKnowledgeBase();
    }

    @Test
    void insertThenFindById_returnsPersistedDocument() {
        Document saved = adapter.insert(kbId, makeDocument(kbId, "test-lesson"));

        Optional<Document> found = adapter.findById(kbId, saved.documentId());
        assertThat(found).isPresent();
        assertThat(found.get().documentId()).isEqualTo(saved.documentId());
        assertThat(found.get().lessonIdentity().lessonId()).isEqualTo("test-lesson");
    }

    @Test
    void aCliDocument_loadsBackWithItsUploadSource() {
        Document saved = adapter.insert(kbId, makeDocument(kbId, "cli-lesson", UploadSource.CLI));

        assertThat(adapter.findById(kbId, saved.documentId())).map(Document::uploadSource).contains(UploadSource.CLI);
    }

    @Test
    void insert_neverOverwritesAnExistingDocument() {
        Document document = makeDocument(kbId, "test-lesson");
        adapter.insert(kbId, document);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
            .isThrownBy(() -> adapter.insert(kbId, document));
    }

    @Test
    void findById_returnsEmpty_forAnotherKnowledgeBasesId() {
        Document saved = adapter.insert(kbId, makeDocument(kbId, "test-lesson"));

        assertThat(adapter.findById(saveKnowledgeBase(), saved.documentId())).isEmpty();
    }

    @Test
    void findByContentHash_returnsDocumentWithMatchingHash() {
        Document saved = adapter.insert(kbId, makeDocument(kbId, "hash-lesson"));

        Optional<Document> found = adapter.findByContentHash(kbId, saved.contentHash());
        assertThat(found).isPresent();
        assertThat(found.get().documentId()).isEqualTo(saved.documentId());
    }

    @Test
    void findByContentHash_returnsEmpty_whenNoMatch() {
        Optional<Document> found = adapter.findByContentHash(
            kbId, ContentHash.compute("nonexistent-content".getBytes()));
        assertThat(found).isEmpty();
    }

    @Test
    void sameHashInTwoKnowledgeBases_isNotADuplicateAcrossThem() {
        UUID otherKbId = saveKnowledgeBase();
        Document first = adapter.insert(kbId, makeDocument(kbId, "shared-lesson"));

        assertThat(adapter.findByContentHash(otherKbId, first.contentHash())).isEmpty();

        Document second = adapter.insert(otherKbId, makeDocument(otherKbId, "shared-lesson"));
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
        assertThat(adapter.findByContentHash(kbId, first.contentHash()))
            .map(Document::documentId).contains(first.documentId());
        assertThat(adapter.findByContentHash(otherKbId, first.contentHash()))
            .map(Document::documentId).contains(second.documentId());
    }

    @Test
    void findLessonTitle_isScopedToTheKnowledgeBase() {
        adapter.insert(kbId, makeDocument(kbId, "bio-3"));

        assertThat(adapter.findLessonTitle(kbId, "bio-3")).contains("bio-3 title");
        assertThat(adapter.findLessonTitle(kbId, "bio-4")).isEmpty();
        assertThat(adapter.findLessonTitle(saveKnowledgeBase(), "bio-3")).isEmpty();
    }

    @Test
    void listByKnowledgeBase_returnsAllDocumentsForKb() {
        adapter.insert(kbId, makeDocument(kbId, "lesson-a"));
        adapter.insert(kbId, makeDocument(kbId, "lesson-b"));

        List<Document> results = adapter.listByKnowledgeBase(kbId);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.knowledgeBaseId().equals(kbId));
    }

    @Test
    void deletingAUser_cascadesThroughTheirKnowledgeBasesToTheirDocuments() {
        adapter.insert(kbId, makeDocument(kbId, "cascade-lesson"));

        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);

        assertThat(adapter.listByKnowledgeBase(kbId)).isEmpty();
    }

    @Test
    void anImageBlock_roundTripsWithEqualBytesAndMimeType() {
        ImageInput image = new ImageInput(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF}, "image/png");
        Document saved = adapter.insert(kbId, makeDocument(kbId, "image-lesson", List.of(
            ContentBlock.text("Before the figure", 0),
            ContentBlock.image(image, 1))));

        Document found = adapter.findById(kbId, saved.documentId()).orElseThrow();

        assertThat(found.contentBlocks()).hasSize(2);
        assertThat(found.contentBlocks().get(0).image()).isNull();
        ContentBlock imageBlock = found.contentBlocks().get(1);
        assertThat(imageBlock).isEqualTo(ContentBlock.image(image, 1));
        assertThat(imageBlock.image().data()).isEqualTo(image.data());
        assertThat(imageBlock.image().mimeType()).isEqualTo("image/png");
    }

    @Test
    void theListAndTheDedupLookup_leaveOutTheBlocks_whileFindByIdKeepsTheImage() {
        ImageInput image = new ImageInput(new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}, "image/png");
        Document saved = adapter.insert(kbId, makeDocument(kbId, "figure-lesson", List.of(
            ContentBlock.text("Before the figure", 0),
            ContentBlock.image(image, 1))));

        assertThat(adapter.listByKnowledgeBase(kbId)).singleElement().satisfies(listed -> {
            assertThat(listed.documentId()).isEqualTo(saved.documentId());
            assertThat(listed.lessonIdentity()).isEqualTo(saved.lessonIdentity());
            assertThat(listed.contentBlocks()).isEmpty();
        });
        assertThat(adapter.findByContentHash(kbId, saved.contentHash())).hasValueSatisfying(identical -> {
            assertThat(identical.documentId()).isEqualTo(saved.documentId());
            assertThat(identical.contentBlocks()).isEmpty();
        });
        assertThat(adapter.findById(kbId, saved.documentId()).orElseThrow().contentBlocks())
            .containsExactly(ContentBlock.text("Before the figure", 0), ContentBlock.image(image, 1));
    }

    @Test
    void findByContentHash_skipsAConversationTurn() {
        Document turn = adapter.insert(kbId, makeDocument(kbId, "conversation", UploadSource.CONVERSATION));

        assertThat(adapter.findByContentHash(kbId, turn.contentHash())).isEmpty();
    }

    @Test
    void aRowWrittenBeforeImageBlocks_readsBackWithNoImages() {
        UUID documentId = UUID.randomUUID();
        String oldShapeBlocks = """
            [{"blockType": "HEADING", "content": "Old", "mediaRef": null, "mediaType": null,
              "metadata": {"level": 1}, "position": 0},
             {"blockType": "TEXT", "content": "Old body", "mediaRef": null, "mediaType": null,
              "metadata": {}, "position": 1}]
            """;
        jdbcTemplate.update("""
            INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,
                source_filename, mime_type, content_blocks, upload_source, uploaded_by)
            VALUES (?, ?, 'old-lesson', 'Old lesson', ?, 'old.md', 'text/markdown', ?::jsonb, 'API', ?)
            """, documentId, kbId, ContentHash.compute("old-row".getBytes()).sha256(), oldShapeBlocks, userId);

        Document found = adapter.findById(kbId, documentId).orElseThrow();

        assertThat(found.contentBlocks()).extracting(ContentBlock::content).containsExactly("Old", "Old body");
        assertThat(found.contentBlocks()).extracting(ContentBlock::image).containsOnlyNulls();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private UUID saveKnowledgeBase() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setKbId(UUID.randomUUID());
        kb.setOwnerId(userId);
        kb.setName("Test KB");
        return knowledgeBaseJpaRepository.save(kb).getKbId();
    }

    private Document makeDocument(UUID kbId, String lessonId) {
        return makeDocument(kbId, lessonId, UploadSource.API);
    }

    private Document makeDocument(UUID kbId, String lessonId, UploadSource source) {
        return makeDocument(kbId, lessonId, source, List.of());
    }

    private Document makeDocument(UUID kbId, String lessonId, List<ContentBlock> contentBlocks) {
        return makeDocument(kbId, lessonId, UploadSource.API, contentBlocks);
    }

    private Document makeDocument(UUID kbId, String lessonId, UploadSource source, List<ContentBlock> contentBlocks) {
        Document base = TestFixtures.makeDocument(null, kbId);
        return new Document(
            base.documentId(), kbId,
            new LessonIdentity(lessonId, lessonId + " title"),
            ContentHash.compute((lessonId + "-unique-content").getBytes()),
            lessonId + ".md", "text/markdown", lessonId + " content",
            contentBlocks, source,
            userId,
            base.createdAt(), base.updatedAt()
        );
    }
}
