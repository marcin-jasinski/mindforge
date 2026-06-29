package dev.mindforge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByContentHash(String contentHash);

    List<DocumentEntity> findByKnowledgeBaseId(UUID knowledgeBaseId);

    @Modifying
    @Query("UPDATE DocumentEntity d SET d.status = :status WHERE d.documentId = :documentId")
    void updateStatus(@Param("documentId") UUID documentId, @Param("status") String status);
}
