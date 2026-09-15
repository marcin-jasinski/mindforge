package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    /** A row lock only means something inside the caller's transaction, so one must already be open. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT kb_id FROM knowledge_bases WHERE kb_id = :knowledgeBaseId FOR UPDATE", nativeQuery = true)
    List<UUID> lockKnowledgeBase(UUID knowledgeBaseId);

    Optional<DocumentEntity> findByKnowledgeBaseIdAndDocumentId(UUID knowledgeBaseId, UUID documentId);

    Optional<DocumentEntity> findByKnowledgeBaseIdAndContentHashAndUploadSourceNot(UUID knowledgeBaseId,
                                                                                   String contentHash,
                                                                                   String uploadSource);

    @Query(value = "SELECT lesson_title FROM documents WHERE knowledge_base_id = :knowledgeBaseId AND lesson_id = :lessonId"
        + " ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<String> findLessonTitle(UUID knowledgeBaseId, String lessonId);

    List<DocumentEntity> findByKnowledgeBaseId(UUID knowledgeBaseId);
}
