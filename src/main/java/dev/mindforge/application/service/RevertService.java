package dev.mindforge.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.RevertNotAllowedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;

/**
 * Reverts a run, or removes one supersession, as a {@code REVERT} run. It makes no model call, so everything happens
 * in one transaction: insert the run, claim the lease, write, complete and release (ADR 0012, T16).
 */
public class RevertService {

    private final IngestRunRepository runs;
    private final WikiStore wiki;
    private final TransactionOperations transactions;

    public RevertService(IngestRunRepository runs, WikiStore wiki, TransactionOperations transactions) {
        this.runs = runs;
        this.wiki = wiki;
        this.transactions = transactions;
    }

    /**
     * Restores forward every page whose highest revision the run wrote, and deletes the run's sources and the
     * supersessions it inserted for those pages only.
     *
     * @return the id of the {@code REVERT} run
     * @throws KnowledgeBaseBusyException while another run holds the lease
     * @throws RevertNotAllowedException when the run is not a completed ingest or Lint run, or no longer tips a page
     */
    public UUID revert(UUID kbId, UUID runId) {
        return transactions.execute(status -> {
            UUID revertRunId = start(kbId, runId);
            boolean revertible = runs.findById(kbId, runId)
                .filter(run -> run.kind() != RunKind.REVERT && run.status() == RunStatus.COMPLETED)
                .isPresent();
            if (!revertible) {
                throw new RevertNotAllowedException("Run " + runId + " is not a completed ingest or Lint run");
            }
            List<UUID> restored = restoreForward(kbId, runId, revertRunId);
            if (restored.isEmpty()) {
                throw new RevertNotAllowedException("Run " + runId + " is no longer the latest to touch any page");
            }
            wiki.deleteSources(kbId, runId, restored);
            finish(kbId, revertRunId, wiki.deleteSupersessionsBySuperseding(kbId, runId, restored));
            return revertRunId;
        });
    }

    /**
     * Removes one supersession. The row is read first only to name its run on the {@code REVERT} run; it is deleted
     * after the lease is claimed, so a concurrent revert of the same rows serializes instead of deadlocking.
     *
     * @return the id of the {@code REVERT} run, which reverts the run that inserted the supersession
     * @throws KnowledgeBaseBusyException while another run holds the lease
     * @throws RevertNotAllowedException when there is no such supersession
     */
    public UUID removeSupersession(UUID kbId, UUID supersessionId) {
        return transactions.execute(status -> {
            UUID insertingRunId = wiki.findSupersession(kbId, supersessionId)
                .map(PageSupersession::ingestRunId)
                .orElseThrow(() -> noSupersession(supersessionId));
            UUID revertRunId = start(kbId, insertingRunId);
            if (!wiki.deleteSupersession(kbId, supersessionId)) {
                throw noSupersession(supersessionId);
            }
            finish(kbId, revertRunId, 1);
            return revertRunId;
        });
    }

    private static RevertNotAllowedException noSupersession(UUID supersessionId) {
        return new RevertNotAllowedException("No supersession " + supersessionId);
    }

    private UUID start(UUID kbId, UUID revertsRunId) {
        IngestRun run = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.REVERT, null, revertsRunId));
        if (!runs.claim(kbId, run.runId())) {
            throw new KnowledgeBaseBusyException(kbId);
        }
        return run.runId();
    }

    private void finish(UUID kbId, UUID revertRunId, int supersessionsRemoved) {
        runs.markWritten(kbId, revertRunId, List.of(), Map.of());
        runs.complete(kbId, revertRunId, supersessionsRemoved, false, List.of(), Map.of());
    }

    /**
     * Brings each page the run still tips back to its revision before the run: a revision carrying that content, a
     * tombstone for a page the run created, or a re-insert for a page it deleted whose path is still free.
     */
    private List<UUID> restoreForward(UUID kbId, UUID runId, UUID revertRunId) {
        List<PageRevision> written = wiki.revisionsByRun(kbId, runId);
        Map<UUID, PageRevision> tips = wiki.tipRevisions(kbId, written.stream().map(PageRevision::pageId).toList());
        List<UUID> restored = new ArrayList<>();
        for (PageRevision revision : written) {
            if (!tips.get(revision.pageId()).ingestRunId().equals(runId)) {
                continue;
            }
            Optional<PageRevision> before = revision.revision() == 1
                ? Optional.empty()
                : wiki.findRevision(kbId, revision.pageId(), revision.revision() - 1).filter(r -> !r.isTombstone());
            if (revision.isTombstone()) {
                if (before.isEmpty() || wiki.findByPath(kbId, revision.path()).isPresent()) {
                    continue;
                }
                wiki.reinsertPage(kbId, before.get(), revertRunId);
            } else if (before.isEmpty()) {
                wiki.deletePage(kbId, revision.pageId(), revertRunId);
            } else {
                wiki.savePage(kbId, before.get().toWrite(), revertRunId);
            }
            restored.add(revision.pageId());
        }
        return restored;
    }
}
