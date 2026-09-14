package dev.mindforge.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.WikiPageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WikiPageJpaRepository extends JpaRepository<WikiPageEntity, UUID> {

    interface IndexRow {
        String getPath();
        String getTitle();
        String getDescription();
        String getPageType();
    }

    interface EdgeRow {
        String getSourcePath();
        String getTargetPath();
    }

    Optional<WikiPageEntity> findByKnowledgeBaseIdAndPath(UUID knowledgeBaseId, String path);

    Optional<WikiPageEntity> findByKnowledgeBaseIdAndPageId(UUID knowledgeBaseId, UUID pageId);

    List<WikiPageEntity> findByKnowledgeBaseIdAndPathIn(UUID knowledgeBaseId, Collection<String> paths);

    List<WikiPageEntity> findByKnowledgeBaseIdAndPageType(UUID knowledgeBaseId, String pageType);

    @Query("SELECT p.path AS path, p.title AS title, p.description AS description, p.pageType AS pageType"
        + " FROM WikiPageEntity p WHERE p.knowledgeBaseId = :knowledgeBaseId")
    List<IndexRow> listIndex(UUID knowledgeBaseId);

    @Query("SELECT DISTINCT p.pageId FROM WikiPageEntity p, PageSourceEntity s, DocumentEntity d"
        + " WHERE p.knowledgeBaseId = :knowledgeBaseId AND p.pageType = :pageType"
        + " AND s.knowledgeBaseId = :knowledgeBaseId AND s.pageId = p.pageId"
        + " AND d.knowledgeBaseId = :knowledgeBaseId AND d.documentId = s.documentId AND d.lessonId = :lessonId")
    List<UUID> findPageIdsForLesson(UUID knowledgeBaseId, String lessonId, String pageType);

    @Query("SELECT DISTINCT p.path AS sourcePath, l.targetPath AS targetPath"
        + " FROM PageLinkEntity l, WikiPageEntity p"
        + " WHERE l.knowledgeBaseId = :knowledgeBaseId AND p.knowledgeBaseId = :knowledgeBaseId"
        + " AND p.pageId = l.sourcePageId")
    List<EdgeRow> listEdges(UUID knowledgeBaseId);
}
