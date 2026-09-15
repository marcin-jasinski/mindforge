package dev.mindforge.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.infrastructure.persistence.entity.IngestRunEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Runs and the lease. The claim locks the knowledge-base row and then a {@code QUEUED} run; commits lock a
 * {@code RUNNING} or {@code WRITTEN} run and then the knowledge-base row. Neither waits on the other's run row, so
 * they cannot deadlock (T17).
 */
public interface IngestRunJpaRepository extends JpaRepository<IngestRunEntity, UUID> {

    interface LogEntryRow {
        UUID getRunId();
        RunKind getKind();
        Instant getFinishedAt();
        String getUploadSource();
        String getLessonId();
        String getLessonTitle();
        Long getCreated();
        Long getRevised();
        Long getDeleted();
        Integer getSupersessionCount();
        RunKind getRevertedKind();
        Instant getRevertedFinishedAt();
        String getRevertedUploadSource();
        String getRevertedLessonId();
        String getRevertedLessonTitle();
    }

    interface RunSummaryRow {
        UUID getRunId();
        RunKind getKind();
        RunStatus getStatus();
        UUID getDocumentId();
        String getUploadSource();
        String getLessonTitle();
        Short getAttempt();
        Boolean getRetryable();
        String getFailureReason();
        Long getCreated();
        Long getRevised();
        Long getDeleted();
        Integer getSupersessionCount();
        Instant getCreatedAt();
        Instant getFinishedAt();
    }

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE knowledge_bases SET active_run_id = :runId"
        + " WHERE kb_id = :knowledgeBaseId AND active_run_id IS NULL", nativeQuery = true)
    int takeLease(UUID knowledgeBaseId, UUID runId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE knowledge_bases SET active_run_id = NULL"
        + " WHERE kb_id = :knowledgeBaseId AND active_run_id = :runId", nativeQuery = true)
    int releaseLease(UUID knowledgeBaseId, UUID runId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE ingest_runs SET status = 'RUNNING', started_at = now()"
        + " WHERE run_id = :runId AND knowledge_base_id = :knowledgeBaseId AND status = 'QUEUED'", nativeQuery = true)
    int markRunning(UUID knowledgeBaseId, UUID runId);

    /** Moves a run that holds the lease from one status to another, stamping {@code finished_at} on a terminal one. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE ingest_runs SET status = :to,"
        + " finished_at = CASE WHEN :to IN ('COMPLETED', 'FAILED') THEN now() ELSE finished_at END"
        + " WHERE run_id = :runId AND knowledge_base_id = :knowledgeBaseId AND status = :from"
        + " AND EXISTS (SELECT 1 FROM knowledge_bases WHERE kb_id = :knowledgeBaseId AND active_run_id = :runId)",
        nativeQuery = true)
    int fence(UUID knowledgeBaseId, UUID runId, String from, String to);

    Optional<IngestRunEntity> findByKnowledgeBaseIdAndRunId(UUID knowledgeBaseId, UUID runId);

    Optional<IngestRunEntity> findFirstByKnowledgeBaseIdAndStatusOrderByCreatedAt(UUID knowledgeBaseId,
                                                                                  RunStatus status);

    Optional<IngestRunEntity> findFirstByKnowledgeBaseIdAndDocumentIdOrderByCreatedAtDesc(UUID knowledgeBaseId,
                                                                                         UUID documentId);

    @Query(value = "SELECT DISTINCT ON (document_id) * FROM ingest_runs"
        + " WHERE knowledge_base_id = :knowledgeBaseId AND document_id IS NOT NULL"
        + " ORDER BY document_id, created_at DESC", nativeQuery = true)
    List<IngestRunEntity> findLatestPerDocument(UUID knowledgeBaseId);

    Optional<IngestRunEntity> findFirstByKnowledgeBaseIdAndKindAndStatusOrderByFinishedAtDesc(UUID knowledgeBaseId,
                                                                                           RunKind kind,
                                                                                           RunStatus status);

    /** Page counts come from the run's revisions, as the log's do. */
    @Query("SELECT r.runId AS runId, r.kind AS kind, r.status AS status, r.documentId AS documentId,"
        + " d.uploadSource AS uploadSource, d.lessonTitle AS lessonTitle, r.attempt AS attempt,"
        + " r.retryable AS retryable, r.failureReason AS failureReason,"
        + " SUM(CASE WHEN pr.revision = 1 THEN 1 ELSE 0 END) AS created,"
        + " SUM(CASE WHEN pr.revision > 1 AND pr.markdownBody IS NOT NULL THEN 1 ELSE 0 END) AS revised,"
        + " SUM(CASE WHEN pr.pageId IS NOT NULL AND pr.markdownBody IS NULL THEN 1 ELSE 0 END) AS deleted,"
        + " r.supersessionCount AS supersessionCount, r.createdAt AS createdAt, r.finishedAt AS finishedAt"
        + " FROM IngestRunEntity r"
        + " LEFT JOIN DocumentEntity d ON d.knowledgeBaseId = :knowledgeBaseId AND d.documentId = r.documentId"
        + " LEFT JOIN PageRevisionEntity pr ON pr.knowledgeBaseId = :knowledgeBaseId AND pr.ingestRunId = r.runId"
        + " WHERE r.knowledgeBaseId = :knowledgeBaseId"
        + " GROUP BY r.runId, r.kind, r.status, r.documentId, d.uploadSource, d.lessonTitle, r.attempt,"
        + " r.retryable, r.failureReason, r.supersessionCount, r.createdAt, r.finishedAt"
        + " ORDER BY r.createdAt DESC")
    List<RunSummaryRow> findRunSummaries(UUID knowledgeBaseId, Limit limit);

    boolean existsByKnowledgeBaseIdAndKindAndStatusIn(UUID knowledgeBaseId, RunKind kind,
                                                      Collection<RunStatus> statuses);

    List<IngestRunEntity> findByStatusIn(Collection<RunStatus> statuses);

    @Query("SELECT DISTINCT r.knowledgeBaseId FROM IngestRunEntity r WHERE r.status = :status")
    List<UUID> findKnowledgeBaseIdsWithStatus(RunStatus status);

    /** Page counts come from the run's revisions, which are never deleted; a revision 1 is never a tombstone. */
    @Query("SELECT r.runId AS runId, r.kind AS kind, r.finishedAt AS finishedAt,"
        + " r.supersessionCount AS supersessionCount,"
        + " d.uploadSource AS uploadSource, d.lessonId AS lessonId, d.lessonTitle AS lessonTitle,"
        + " SUM(CASE WHEN pr.revision = 1 THEN 1 ELSE 0 END) AS created,"
        + " SUM(CASE WHEN pr.revision > 1 AND pr.markdownBody IS NOT NULL THEN 1 ELSE 0 END) AS revised,"
        + " SUM(CASE WHEN pr.pageId IS NOT NULL AND pr.markdownBody IS NULL THEN 1 ELSE 0 END) AS deleted,"
        + " reverted.kind AS revertedKind, reverted.finishedAt AS revertedFinishedAt,"
        + " revertedDocument.uploadSource AS revertedUploadSource, revertedDocument.lessonId AS revertedLessonId,"
        + " revertedDocument.lessonTitle AS revertedLessonTitle"
        + " FROM IngestRunEntity r"
        + " LEFT JOIN DocumentEntity d ON d.knowledgeBaseId = :knowledgeBaseId AND d.documentId = r.documentId"
        + " LEFT JOIN IngestRunEntity reverted"
        + "   ON reverted.knowledgeBaseId = :knowledgeBaseId AND reverted.runId = r.revertsRunId"
        + " LEFT JOIN DocumentEntity revertedDocument"
        + "   ON revertedDocument.knowledgeBaseId = :knowledgeBaseId"
        + "   AND revertedDocument.documentId = reverted.documentId"
        + " LEFT JOIN PageRevisionEntity pr ON pr.knowledgeBaseId = :knowledgeBaseId AND pr.ingestRunId = r.runId"
        + " WHERE r.knowledgeBaseId = :knowledgeBaseId AND r.status = :status"
        + " GROUP BY r.runId, r.kind, r.finishedAt, r.supersessionCount,"
        + " d.uploadSource, d.lessonId, d.lessonTitle, reverted.kind, reverted.finishedAt,"
        + " revertedDocument.uploadSource, revertedDocument.lessonId, revertedDocument.lessonTitle"
        + " ORDER BY r.finishedAt DESC")
    List<LogEntryRow> findLogEntries(UUID knowledgeBaseId, RunStatus status);
}
