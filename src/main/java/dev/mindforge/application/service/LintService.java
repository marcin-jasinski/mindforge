package dev.mindforge.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import dev.mindforge.domain.model.RunFailure;
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
        RunState state = new RunState(run, settings);
        String failure = "interrupted";
        try {
            review(state);
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
                fail(state, failure);
            }
        }
    }

    /** Concept pages loaded per round; a round's bodies are the most Lint holds at once. */
    private static final int PAGES_PER_ROUND = 50;

    private static final int CONCURRENT_CHUNKS = 4;

    /** What one chunk contributes: its verified link insertions, its findings and its dropped proposals. */
    private record ChunkResult(List<PageWrite> linked, List<ReviewItem> findings, int dropped) {}

    private void review(RunState state) {
        IngestRun run = state.run;
        UUID kbId = state.kbId();
        List<IndexEntry> index = wiki.listIndex(kbId);
        String renderedIndex = IndexRenderer.render(index);
        Set<String> livePaths = index.stream().map(IndexEntry::path).collect(Collectors.toSet());
        Set<String> targetPaths = index.stream()
            .filter(entry -> entry.type().equals(PageType.CONCEPT) || entry.type().equals(PageType.SOURCE_SUMMARY))
            .map(IndexEntry::path).collect(Collectors.toSet());
        List<String> conceptPaths = index.stream().filter(entry -> entry.type().equals(PageType.CONCEPT))
            .map(IndexEntry::path).sorted().toList();

        Map<String, PageWrite> linked = new LinkedHashMap<>();
        List<ReviewItem> findings = new ArrayList<>();
        int dropped = 0;
        int rounds = (conceptPaths.size() + PAGES_PER_ROUND - 1) / PAGES_PER_ROUND;
        for (int round = 0; round < rounds; round++) {
            progress.notify(kbId, RunProgress.step(run, "review", round, rounds));
            List<String> paths = conceptPaths.subList(round * PAGES_PER_ROUND,
                Math.min(conceptPaths.size(), (round + 1) * PAGES_PER_ROUND));
            List<WikiPage> pages = wiki.findByPaths(kbId, paths).stream()
                .sorted(Comparator.comparing(WikiPage::path)).toList();
            for (ChunkResult result : reviewAll(state, chunks(pages), renderedIndex, livePaths, targetPaths)) {
                result.linked().forEach(write -> linked.put(write.path(), write));
                findings.addAll(result.findings());
                dropped += result.dropped();
            }
        }
        if (dropped > 0) {
            state.addFailure(RunFailure.dropped("linkCheck", dropped));
        }

        transactions.executeWithoutResult(status -> {
            runs.markWritten(kbId, run.runId(), state.failures(), state.versions());
            linked.values().forEach(write -> wiki.savePage(kbId, write, run.runId()));
            runs.recordFindings(kbId, run.runId(), findings);
            runs.complete(kbId, run.runId(), 0, false, state.failures(), state.versions());
        });
    }

    /**
     * A chunk's two model calls are independent of other chunks, so a round's chunks run concurrently, as in ingest —
     * at most {@link #CONCURRENT_CHUNKS} at once, below the connection pool, since a chunk may also read targets.
     */
    private List<ChunkResult> reviewAll(RunState state, List<List<WikiPage>> chunks, String renderedIndex,
                                        Set<String> livePaths, Set<String> targetPaths) {
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CHUNKS, Thread.ofVirtual().factory())) {
            List<Future<ChunkResult>> futures = chunks.stream()
                .map(chunk -> executor.submit(() -> reviewChunk(state, chunk, renderedIndex, livePaths, targetPaths)))
                .toList();
            List<ChunkResult> results = new ArrayList<>();
            for (Future<ChunkResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while linting pages", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("a lint chunk crashed", e.getCause());
        }
    }

    private ChunkResult reviewChunk(RunState state, List<WikiPage> chunk, String renderedIndex, Set<String> livePaths,
                                    Set<String> targetPaths) {
        Map<String, String> bodies = new LinkedHashMap<>();
        chunk.forEach(page -> bodies.put(page.path(), page.markdownBody()));
        List<PageWrite> linked = new ArrayList<>();
        List<ReviewItem> findings = new ArrayList<>();
        int dropped = 0;
        try {
            List<LinkInsertion> proposals = linkChecker.check(bodies, renderedIndex);
            state.version(LinkChecker.class, LinkChecker.VERSION, LinkChecker.TIER);
            Map<String, String> targets = targetBodies(state.kbId(), proposals, targetPaths);
            Map<String, List<LinkInsertion>> byPage = proposals.stream()
                .collect(Collectors.groupingBy(proposal -> String.valueOf(proposal.pagePath())));
            for (WikiPage page : chunk) {
                LinkInsertionApplier.Result result = LinkInsertionApplier.apply(page.path(), page.markdownBody(),
                    byPage.getOrDefault(page.path(), List.of()), targets);
                dropped += result.dropped();
                if (result.applied() > 0) {
                    linked.add(new PageWrite(page.pageId(), page.path(), page.title(), page.description(), page.type(),
                        result.body()));
                }
            }
            dropped += (int) proposals.stream().filter(proposal -> !bodies.containsKey(proposal.pagePath())).count();
        } catch (RuntimeException e) {
            log.warn("Link check of Lint run {} failed; a chunk gets no links", state.run.runId(), e);
            linked.clear();
            dropped = 0;
            state.addFailure(RunFailure.of("linkCheck", IngestRunFailedException.reasonFor(e)));
        }
        try {
            reviewer.review(bodies, renderedIndex).stream()
                .map(item -> new ReviewItem(item.kind(), item.pages().stream().filter(livePaths::contains).toList(),
                    item.text()))
                .forEach(findings::add);
            state.version(WikiReviewer.class, WikiReviewer.VERSION, WikiReviewer.TIER);
        } catch (RuntimeException e) {
            log.warn("Review of Lint run {} failed; a chunk gets no findings", state.run.runId(), e);
            state.addFailure(RunFailure.of("review", IngestRunFailedException.reasonFor(e)));
        }
        return new ChunkResult(linked, findings, dropped);
    }

    /**
     * What {@link LinkInsertionApplier} needs of the targets: that each is a live Concept or Source Summary, and the
     * body only of one a fragment points into. A target without a fragment maps to an empty body, so no whole-wiki
     * body map is built.
     */
    private Map<String, String> targetBodies(UUID kbId, List<LinkInsertion> proposals, Set<String> targetPaths) {
        Map<String, String> targets = new HashMap<>();
        Set<String> needBody = new HashSet<>();
        for (LinkInsertion proposal : proposals) {
            String target = proposal.targetPath();
            if (target != null && targetPaths.contains(target)) {
                targets.put(target, "");
                if (proposal.fragment() != null) {
                    needBody.add(target);
                }
            }
        }
        if (!needBody.isEmpty()) {
            wiki.findByPaths(kbId, needBody).forEach(page -> targets.put(page.path(), page.markdownBody()));
        }
        return targets;
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

    private void fail(RunState state, String reason) {
        IngestRun run = state.run;
        try {
            runs.fail(state.kbId(), run.runId(), reason, true, state.failures(), state.versions());
            progress.notify(run.knowledgeBaseId(), RunProgress.status(run, RunStatus.FAILED));
        } catch (RunFencedException e) {
            log.warn("Lint run {} lost its lease before failing: {}", run.runId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not record the failure of Lint run {}; the sweep settles it", run.runId(), e);
        }
    }
}
