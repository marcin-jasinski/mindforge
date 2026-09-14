package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.infrastructure.persistence.entity.IngestRunEntity;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.IngestRunEntityMapper;

@Transactional
public class IngestRunRepositoryAdapter implements IngestRunRepository {

    private static final List<RunStatus> ACTIVE = List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.WRITTEN);
    private static final List<RunStatus> UNFINISHED = List.of(RunStatus.RUNNING, RunStatus.WRITTEN);

    private final IngestRunJpaRepository runs;
    private final IngestRunEntityMapper mapper;

    public IngestRunRepositoryAdapter(IngestRunJpaRepository runs, IngestRunEntityMapper mapper) {
        this.runs = runs;
        this.mapper = mapper;
    }

    @Override
    public IngestRun enqueue(UUID kbId, IngestRun run) {
        if (!kbId.equals(run.knowledgeBaseId()) || run.status() != RunStatus.QUEUED) {
            throw new IllegalArgumentException("Run " + run.runId() + " is not a queued run of knowledge base " + kbId);
        }
        return mapper.toDomain(runs.saveAndFlush(mapper.toEntity(run)));
    }

    /** A lost lease changes nothing; a run that is not queued gives the lease straight back. */
    @Override
    public boolean claim(UUID kbId, UUID runId) {
        if (runs.takeLease(kbId, runId) == 0) {
            return false;
        }
        if (runs.markRunning(kbId, runId) == 1) {
            return true;
        }
        runs.releaseLease(kbId, runId);
        return false;
    }

    @Override
    public Optional<IngestRun> oldestQueued(UUID kbId) {
        return runs.findFirstByKnowledgeBaseIdAndStatusOrderByCreatedAt(kbId, RunStatus.QUEUED).map(mapper::toDomain);
    }

    @Override
    public void markWritten(UUID kbId, UUID runId, List<Map<String, Object>> failures,
                            Map<String, String> stepVersions) {
        IngestRunEntity run = fence(kbId, runId, RunStatus.RUNNING, RunStatus.WRITTEN);
        run.setFailures(failures);
        run.setStepVersions(stepVersions);
    }

    @Override
    public void complete(UUID kbId, UUID runId, int supersessionCount, boolean supersessionSkipped,
                         List<Map<String, Object>> failures, Map<String, String> stepVersions) {
        IngestRunEntity run = fence(kbId, runId, RunStatus.WRITTEN, RunStatus.COMPLETED);
        run.setSupersessionCount(supersessionCount);
        run.setSupersessionSkipped(supersessionSkipped);
        run.setFailures(failures);
        run.setStepVersions(stepVersions);
        runs.releaseLease(kbId, runId);
    }

    @Override
    public void fail(UUID kbId, UUID runId, String reason, boolean retryable, List<Map<String, Object>> failures,
                     Map<String, String> stepVersions) {
        IngestRunEntity run = fence(kbId, runId, RunStatus.RUNNING, RunStatus.FAILED);
        run.setFailureReason(reason);
        run.setRetryable(retryable);
        run.setFailures(failures);
        run.setStepVersions(stepVersions);
        runs.releaseLease(kbId, runId);
    }

    @Override
    public Optional<IngestRun> findById(UUID kbId, UUID runId) {
        return runs.findByKnowledgeBaseIdAndRunId(kbId, runId).map(mapper::toDomain);
    }

    @Override
    public Optional<IngestRun> latestForDocument(UUID kbId, UUID documentId) {
        return runs.findFirstByKnowledgeBaseIdAndDocumentIdOrderByCreatedAtDesc(kbId, documentId)
            .map(mapper::toDomain);
    }

    @Override
    public boolean hasQueuedOrActive(UUID kbId, RunKind kind) {
        return runs.existsByKnowledgeBaseIdAndKindAndStatusIn(kbId, kind, ACTIVE);
    }

    @Override
    public List<IngestRun> findUnfinished() {
        return runs.findByStatusIn(UNFINISHED).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UUID> knowledgeBasesWithQueuedRuns() {
        return runs.findKnowledgeBaseIdsWithStatus(RunStatus.QUEUED);
    }

    /** Returns the managed run, whose remaining columns the caller's transaction then writes. */
    private IngestRunEntity fence(UUID kbId, UUID runId, RunStatus from, RunStatus to) {
        if (runs.fence(kbId, runId, from.name(), to.name()) == 0) {
            throw new RunFencedException(runId, from);
        }
        return runs.findByKnowledgeBaseIdAndRunId(kbId, runId).orElseThrow();
    }
}
