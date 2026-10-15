package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.PageLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PageLinkJpaRepository extends JpaRepository<PageLinkEntity, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PageLinkEntity l WHERE l.knowledgeBaseId = :knowledgeBaseId AND l.sourcePageId = :pageId")
    void deleteBySourcePage(UUID knowledgeBaseId, UUID pageId);

    List<PageLinkEntity> findByKnowledgeBaseIdAndSourcePageIdIn(UUID knowledgeBaseId, Collection<UUID> pageIds);

    List<PageLinkEntity> findByKnowledgeBaseIdAndTargetPathIn(UUID knowledgeBaseId, Collection<String> paths);
}
