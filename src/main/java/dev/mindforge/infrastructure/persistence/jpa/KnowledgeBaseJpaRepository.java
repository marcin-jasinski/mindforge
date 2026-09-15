package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface KnowledgeBaseJpaRepository extends JpaRepository<KnowledgeBaseEntity, UUID> {

    List<KnowledgeBaseEntity> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    /** Conditional on the lease, so it serializes with a claim and never deletes under an active run (T24). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM knowledge_bases WHERE kb_id = :kbId AND active_run_id IS NULL", nativeQuery = true)
    int deleteIfIdle(UUID kbId);
}
