package dev.mindforge.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.WikiReviewer;
import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.application.wiki.LinkInsertionApplier;
import dev.mindforge.domain.model.DomainEvent;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.IngestRunFailedException;
import dev.mindforge.domain.model.LinkInsertion;
import dev.mindforge.domain.model.LintAlreadyQueuedException;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.ReviewItem;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.WikiStore;

/**
 * The full Lint (ADR 0017, T10): a queued {@code LINT} run that reads live Concepts in chunks, applies verified link
 * insertions and stores findings and suggestions on the run. Its only write is a link insertion — it never writes
 * prose, never inserts a supersession and never makes a page from a suggestion.
 */
public class LintService {

    private static final Logger log = LoggerFactory.getLogger(LintService.class);

    private final WikiStore wiki;
    private final IngestRunRepository runs;
    private final DocumentRepository documents;
    private final LinkChecker linkChecker;
    private final WikiReviewer reviewer;
    private final EventPublisher events;
    private final ProgressNotifier progress;
    private final TransactionOperations transactions;
    private final ProcessingSettings settings;

    public LintService(WikiStore wiki, IngestRunRepository runs, DocumentRepository documents, LinkChecker linkChecker,
                       WikiReviewer reviewer, EventPublisher events, ProgressNotifier progress,
                       TransactionOperations transactions, ProcessingSettings settings) {
        this.wiki = wiki;
        this.runs = runs;
        this.documents = documents;
        this.linkChecker = linkChecker;
        this.reviewer = reviewer;
        this.events = events;
        this.progress = progress;
        this.transactions = transactions;
        this.settings = settings;
    }

    /**
     * Queues a full Lint behind any active run.
     *
     * @throws LintAlreadyQueuedException while a {@code LINT} run is queued or running
     */
    public UUID request(UUID kbId) {
        IngestRun run = transactions.execute(status -> {
            documents.lockKnowledgeBase(kbId);
            if (runs.hasQueuedOrActive(kbId, RunKind.LINT)) {
                throw new LintAlreadyQueuedException(kbId);
            }
            IngestRun queued = runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.LINT, null, null));
            events.publish(new DomainEvent.IngestRunQueued(queued.runId(), kbId, Instant.now()));
            return queued;
        });
        progress.notify(kbId, RunProgress.status(run, RunStatus.QUEUED));
        return run.runId();
    }

    /** Runs a claimed {@code LINT} run to {@code COMPLETED} or {@code FAILED}, unless it is fenced. */
    public void run(IngestRun run) {
        UUID kbId = run.knowledgeBaseId();
        List<Map<String, Object>> failures = new ArrayList<>();
        Map<String, String> versions = new HashMap<>();
        String failure = "interrupted";
        try {
            review(run, failures, versions);
            failure = null;
            progress.notify(kbId, RunProgress.status(run, RunStatus.COMPLETED));
        } catch (RunFencedException e) {
            log.warn("Lint run {} lost its lease; nothing written: {}", run.runId(), e.getMessage());
            failure = null;
        } catch (RuntimeException e) {
            log.error("Lint run {} failed", run.runId(), e);
            failure = IngestRunFailedException.reasonFor(e);
        } finally {
            if (failure != null) {
                fail(run, failure, failures, versions);
            }
        }
    }

    private void review(IngestRun run, List<Map<String, Object>> failures, Map<String, String> versions) {
        UUID kbId = run.knowledgeBaseId();
        List<IndexEntry> index = wiki.listIndex(kbId);
        String renderedIndex = IndexRenderer.render(index);
        Set<String> livePaths = index.stream().map(IndexEntry::path).collect(Collectors.toSet());
        List<WikiPage> concepts = wiki.listBodies(kbId, PageType.CONCEPT);
        Map<String, String> targets = new HashMap<>();
        concepts.forEach(page -> targets.put(page.path(), page.markdownBody()));
        wiki.listBodies(kbId, PageType.SOURCE_SUMMARY).forEach(page -> targets.put(page.path(), page.markdownBody()));

        List<List<WikiPage>> chunks = chunks(concepts);
        Map<String, PageWrite> linked = new LinkedHashMap<>();
        List<Map<String, Object>> findings = new ArrayList<>();
        int dropped = 0;
        for (int i = 0; i < chunks.size(); i++) {
            progress.notify(kbId, RunProgress.step(run, "review", i, chunks.size()));
            Map<String, String> bodies = new LinkedHashMap<>();
            chunks.get(i).forEach(page -> bodies.put(page.path(), page.markdownBody()));
            try {
                List<LinkInsertion> proposals = linkChecker.check(bodies, renderedIndex);
                versions.put(LinkChecker.class.getSimpleName(), settings.stepVersion(LinkChecker.VERSION, LinkChecker.TIER));
                Map<String, List<LinkInsertion>> byPage = proposals.stream()
                    .collect(Collectors.groupingBy(proposal -> String.valueOf(proposal.pagePath())));
                for (WikiPage page : chunks.get(i)) {
                    LinkInsertionApplier.Result result = LinkInsertionApplier.apply(page.path(), page.markdownBody(),
                        byPage.getOrDefault(page.path(), List.of()), targets);
                    dropped += result.dropped();
                    if (result.applied() > 0) {
                        linked.put(page.path(), new PageWrite(page.pageId(), page.path(), page.title(),
                            page.description(), page.type(), result.body()));
                    }
                }
                dropped += (int) proposals.stream().filter(proposal -> !bodies.containsKey(proposal.pagePath())).count();
            } catch (RuntimeException e) {
                log.warn("Link check of Lint run {} failed; chunk {} gets no links", run.runId(), i, e);
                failures.add(Map.of("step", "linkCheck", "reason", IngestRunFailedException.reasonFor(e)));
            }
            try {
                reviewer.review(bodies, renderedIndex).stream()
                    .map(item -> new ReviewItem(item.kind(), item.pages().stream().filter(livePaths::contains).toList(),
                        item.text()))
                    .forEach(item -> findings.add(item.toRow()));
                versions.put(WikiReviewer.class.getSimpleName(),
                    settings.stepVersion(WikiReviewer.VERSION, WikiReviewer.TIER));
            } catch (RuntimeException e) {
                log.warn("Review of Lint run {} failed; chunk {} gets no findings", run.runId(), i, e);
                failures.add(Map.of("step", "review", "reason", IngestRunFailedException.reasonFor(e)));
            }
        }
        if (dropped > 0) {
            failures.add(Map.of("step", "linkCheck", "dropped", dropped));
        }

        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, run.runId(), failures, versions);
            linked.values().forEach(write -> wiki.savePage(kbId, write, run.runId()));
            runs.recordFindings(kbId, run.runId(), findings);
            runs.complete(kbId, run.runId(), 0, false, failures, versions);
        });
    }

    /** Pages in index order, grouped until the next page would pass the chunk budget; a larger page stands alone. */
    private List<List<WikiPage>> chunks(List<WikiPage> pages) {
        List<List<WikiPage>> chunks = new ArrayList<>();
        int tokens = 0;
        for (WikiPage page : pages) {
            int pageTokens = TokenEstimate.of(page.markdownBody());
            if (chunks.isEmpty() || tokens + pageTokens > settings.chunkSizeTokens()) {
                chunks.add(new ArrayList<>());
                tokens = 0;
            }
            chunks.getLast().add(page);
            tokens += pageTokens;
        }
        return chunks;
    }

    private void fail(IngestRun run, String reason, List<Map<String, Object>> failures, Map<String, String> versions) {
        try {
            runs.fail(run.knowledgeBaseId(), run.runId(), reason, true, failures, versions);
            progress.notify(run.knowledgeBaseId(), RunProgress.status(run, RunStatus.FAILED));
        } catch (RunFencedException e) {
            log.warn("Lint run {} lost its lease before failing: {}", run.runId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not record the failure of Lint run {}; the sweep settles it", run.runId(), e);
        }
    }
}
