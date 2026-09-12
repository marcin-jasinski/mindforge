package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByKnowledgeBaseIdAndDocumentId(UUID knowledgeBaseId, UUID documentId);

    Optional<DocumentEntity> findByKnowledgeBaseIdAndContentHash(UUID knowledgeBaseId, String contentHash);

    List<DocumentEntity> findByKnowledgeBaseId(UUID knowledgeBaseId);
}
