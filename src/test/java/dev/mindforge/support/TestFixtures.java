package dev.mindforge.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.DocumentArtifact;
import dev.mindforge.domain.model.DocumentStatus;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.User;

/**
 * Static factory methods for domain objects used in tests. Each accepts the fields
 * a test cares about and fills the rest with sensible defaults.
 */
public final class TestFixtures {

    private TestFixtures() {}

    public static Document makeDocument(UUID documentId, UUID knowledgeBaseId, DocumentStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new Document(
            documentId != null ? documentId : UUID.randomUUID(),
            knowledgeBaseId != null ? knowledgeBaseId : UUID.randomUUID(),
            new LessonIdentity("test-lesson", "Test Lesson"),
            ContentHash.compute("test content".getBytes()),
            "test-lesson.md",
            "text/markdown",
            "test content",
            List.of(),
            UploadSource.API,
            UUID.randomUUID(),
            status != null ? status : DocumentStatus.PENDING,
            now,
            now);
    }

    public static KnowledgeBase makeKnowledgeBase(UUID kbId, UUID ownerId) {
        return new KnowledgeBase(
            kbId != null ? kbId : UUID.randomUUID(),
            ownerId != null ? ownerId : UUID.randomUUID(),
            "Test KB",
            "A knowledge base used in tests",
            Instant.parse("2026-01-01T00:00:00Z"),
            0);
    }

    public static User makeUser(UUID userId, String email) {
        return new User(
            userId != null ? userId : UUID.randomUUID(),
            "Test User",
            email != null ? email : "test@example.com",
            "$2a$12$placeholderhashplaceholderhashplaceholderhashplaceholder",
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            null);
    }

    public static DocumentArtifact makeDocumentArtifact(UUID documentId, UUID knowledgeBaseId) {
        return new DocumentArtifact(
            UUID.randomUUID(),
            documentId != null ? documentId : UUID.randomUUID(),
            knowledgeBaseId != null ? knowledgeBaseId : UUID.randomUUID(),
            null,
            List.of(),
            null,
            List.of(),
            null,
            Map.of(),
            null,
            Instant.parse("2026-01-01T00:00:00Z"));
    }
}
