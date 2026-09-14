package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.PageRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PageRevisionJpaRepository extends JpaRepository<PageRevisionEntity, PageRevisionEntity.Key> {

    List<PageRevisionEntity> findByKnowledgeBaseIdAndIngestRunId(UUID knowledgeBaseId, UUID ingestRunId);

    Optional<PageRevisionEntity> findByKnowledgeBaseIdAndPageIdAndRevision(UUID knowledgeBaseId, UUID pageId,
                                                                           int revision);

    List<PageRevisionEntity> findByKnowledgeBaseIdAndPageIdOrderByRevision(UUID knowledgeBaseId, UUID pageId);

    @Query("SELECT r FROM PageRevisionEntity r WHERE r.knowledgeBaseId = :knowledgeBaseId AND r.pageId IN :pageIds"
        + " AND r.revision = (SELECT MAX(t.revision) FROM PageRevisionEntity t"
        + " WHERE t.knowledgeBaseId = :knowledgeBaseId AND t.pageId = r.pageId)")
    List<PageRevisionEntity> findTips(UUID knowledgeBaseId, Collection<UUID> pageIds);
}
