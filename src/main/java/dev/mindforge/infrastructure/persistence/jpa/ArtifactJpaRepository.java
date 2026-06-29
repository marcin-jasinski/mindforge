package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactJpaRepository extends JpaRepository<ArtifactEntity, UUID> {

    Optional<ArtifactEntity> findByDocumentId(UUID documentId);

    @Query(value = """
        SELECT COALESCE(SUM(jsonb_array_length(flashcards)), 0)
        FROM artifacts
        WHERE knowledge_base_id = :kbId
        """, nativeQuery = true)
    Long countFlashcardsByKnowledgeBaseId(@Param("kbId") UUID kbId);
}
