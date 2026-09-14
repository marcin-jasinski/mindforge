package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.PageSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PageSourceJpaRepository extends JpaRepository<PageSourceEntity, PageSourceEntity.Key> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PageSourceEntity s WHERE s.knowledgeBaseId = :knowledgeBaseId"
        + " AND s.ingestRunId = :runId AND s.pageId IN :pageIds")
    int deleteByRunAndPages(UUID knowledgeBaseId, UUID runId, Collection<UUID> pageIds);
}
