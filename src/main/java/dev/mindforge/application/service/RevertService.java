package dev.mindforge.application.service;

import java.time.Instant;
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
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.WikiStore;

/**
 * Reverts a run, or removes one supersession, as a {@code REVERT} run. It makes no model call, so everything happens
 * in one transaction: insert the run, claim the lease, write, complete and release (ADR 0012, T16). Progress is
 * notified once the transaction has committed.
 */
public class RevertService {

    private final IngestRunRepository runs;
    private final WikiStore wiki;
    private final ProgressNotifier progress;
    private final TransactionOperations transactions;

    public RevertService(IngestRunRepository runs, WikiStore wiki, ProgressNotifier progress,
                         TransactionOperations transactions) {
        this.runs = runs;
        this.wiki = wiki;
        this.progress = progress;
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
        UUID revertRunId = transactions.execute(status -> {
            UUID started = start(kbId, runId);
            if (!isRevertible(kbId, runId)) {
                throw new RevertNotAllowedException("Run " + runId + " is not a completed ingest or Lint run");
            }
            List<Restore> restores = restorable(kbId, runId);
            if (restores.isEmpty()) {
                throw new RevertNotAllowedException("Run " + runId + " is no longer the latest to touch any page");
            }
            restores.forEach(restore -> restore.apply(wiki, kbId, started));
            List<UUID> restored = restores.stream().map(restore -> restore.written().pageId()).toList();
            wiki.deleteSources(kbId, runId, restored);
            finish(kbId, started, wiki.deleteSupersessionsBySuperseding(kbId, runId, restored));
            return started;
        });
        notifyCompleted(kbId, revertRunId, runId);
        return revertRunId;
    }

    /** Whether {@link #revert} would restore something now: a completed ingest or Lint run that still tips a page. */
    public boolean isOffered(UUID kbId, UUID runId) {
        return isRevertible(kbId, runId) && !restorable(kbId, runId).isEmpty();
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
        UUID[] insertingRunId = new UUID[1];
        UUID revertRunId = transactions.execute(status -> {
            insertingRunId[0] = wiki.findSupersession(kbId, supersessionId)
                .map(PageSupersession::ingestRunId)
                .orElseThrow(() -> noSupersession(supersessionId));
            UUID started = start(kbId, insertingRunId[0]);
            if (!wiki.deleteSupersession(kbId, supersessionId)) {
                throw noSupersession(supersessionId);
            }
            finish(kbId, started, 1);
            return started;
        });
        notifyCompleted(kbId, revertRunId, insertingRunId[0]);
        return revertRunId;
    }

    private static RevertNotAllowedException noSupersession(UUID supersessionId) {
        return new RevertNotAllowedException("No supersession " + supersessionId);
    }

    private boolean isRevertible(UUID kbId, UUID runId) {
        return runs.findById(kbId, runId)
            .filter(run -> run.kind() != RunKind.REVERT && run.status() == RunStatus.COMPLETED)
            .isPresent();
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

    private void notifyCompleted(UUID kbId, UUID revertRunId, UUID revertsRunId) {
        progress.notify(kbId, new RunProgress(revertRunId, RunKind.REVERT, null, RunStatus.COMPLETED, null, null, null,
            Instant.now()));
    }

    /**
     * Each page the run still tips, with its revision before the run: a revision carrying that content, a tombstone
     * for a page the run created, or a re-insert for a page it deleted whose path is still free.
     */
    private List<Restore> restorable(UUID kbId, UUID runId) {
        List<PageRevision> written = wiki.revisionsByRun(kbId, runId);
        Map<UUID, PageRevision> tips = wiki.tipRevisions(kbId, written.stream().map(PageRevision::pageId).toList());
        List<Restore> restores = new ArrayList<>();
        for (PageRevision revision : written) {
            if (!tips.get(revision.pageId()).ingestRunId().equals(runId)) {
                continue;
            }
            Optional<PageRevision> before = revision.revision() == 1
                ? Optional.empty()
                : wiki.findRevision(kbId, revision.pageId(), revision.revision() - 1).filter(r -> !r.isTombstone());
            if (revision.isTombstone() && (before.isEmpty() || wiki.findByPath(kbId, revision.path()).isPresent())) {
                continue;
            }
            restores.add(new Restore(revision, before));
        }
        return restores;
    }

    private record Restore(PageRevision written, Optional<PageRevision> before) {

        private void apply(WikiStore wiki, UUID kbId, UUID revertRunId) {
            if (written.isTombstone()) {
                wiki.reinsertPage(kbId, before.orElseThrow(), revertRunId);
            } else if (before.isEmpty()) {
                wiki.deletePage(kbId, written.pageId(), revertRunId);
            } else {
                wiki.savePage(kbId, before.get().toWrite(), revertRunId);
            }
        }
    }
}
