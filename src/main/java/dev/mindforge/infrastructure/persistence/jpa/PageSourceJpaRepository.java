package dev.mindforge.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.PageSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PageSourceJpaRepository extends JpaRepository<PageSourceEntity, PageSourceEntity.Key> {

    interface CitationRow {
        UUID getPageId();
        UUID getDocumentId();
        String getLessonId();
        String getLessonTitle();
        String getSourceFilename();
        String getUploadSource();
        Instant getUploadedAt();
    }

    @Query("SELECT DISTINCT s.pageId AS pageId, d.documentId AS documentId, d.lessonId AS lessonId,"
        + " d.lessonTitle AS lessonTitle, d.sourceFilename AS sourceFilename, d.uploadSource AS uploadSource,"
        + " d.createdAt AS uploadedAt"
        + " FROM PageSourceEntity s, DocumentEntity d"
        + " WHERE s.knowledgeBaseId = :knowledgeBaseId AND s.pageId IN :pageIds"
        + " AND d.knowledgeBaseId = :knowledgeBaseId AND d.documentId = s.documentId"
        + " ORDER BY uploadedAt")
    List<CitationRow> findCitations(UUID knowledgeBaseId, Collection<UUID> pageIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PageSourceEntity s WHERE s.knowledgeBaseId = :knowledgeBaseId"
        + " AND s.ingestRunId = :runId AND s.pageId IN :pageIds")
    int deleteByRunAndPages(UUID knowledgeBaseId, UUID runId, Collection<UUID> pageIds);
}
