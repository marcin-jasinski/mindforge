package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.infrastructure.persistence.entity.PageSupersessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PageSupersessionJpaRepository extends JpaRepository<PageSupersessionEntity, UUID> {

    Optional<PageSupersessionEntity> findByKnowledgeBaseIdAndSupersessionId(UUID knowledgeBaseId, UUID supersessionId);

    /** A bulk delete, so a row removed concurrently counts as 0 instead of failing an entity delete. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PageSupersessionEntity s WHERE s.knowledgeBaseId = :knowledgeBaseId"
        + " AND s.supersessionId = :supersessionId")
    int deleteOne(UUID knowledgeBaseId, UUID supersessionId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PageSupersessionEntity s WHERE s.knowledgeBaseId = :knowledgeBaseId"
        + " AND s.ingestRunId = :runId AND s.supersedingPageId IN :pageIds")
    int deleteByRunAndSupersedingPages(UUID knowledgeBaseId, UUID runId, Collection<UUID> pageIds);

    @Query("SELECT new dev.mindforge.domain.model.LiveSupersession(s.supersessionId, s.supersededPageId,"
        + " s.sectionAnchor, s.supersedingPageId, superseding.path, superseding.title)"
        + " FROM PageSupersessionEntity s, WikiPageEntity superseded, WikiPageEntity superseding"
        + " WHERE s.knowledgeBaseId = :knowledgeBaseId AND s.supersededPageId IN :pageIds"
        + " AND superseded.knowledgeBaseId = :knowledgeBaseId AND superseded.pageId = s.supersededPageId"
        + " AND superseding.knowledgeBaseId = :knowledgeBaseId AND superseding.pageId = s.supersedingPageId")
    List<LiveSupersession> findLive(UUID knowledgeBaseId, Collection<UUID> pageIds);
}
