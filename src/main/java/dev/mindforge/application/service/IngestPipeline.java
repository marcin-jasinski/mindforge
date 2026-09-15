package dev.mindforge.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.agent.ClaimExtractor;
import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.PageWriter;
import dev.mindforge.agent.RelevanceGuard;
import dev.mindforge.agent.SupersessionDetector;
import dev.mindforge.application.ingest.DraftValidator;
import dev.mindforge.application.ingest.HeadingChunker;
import dev.mindforge.application.ingest.Preprocessor;
import dev.mindforge.application.ingest.Resolver;
import dev.mindforge.application.ingest.SupersessionInputs;
import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.application.wiki.LinkInsertionApplier;
import dev.mindforge.domain.model.Claim;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ConversationTurn;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.EditItem;
import dev.mindforge.domain.model.ExtractResult;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.IngestRunFailedException;
import dev.mindforge.domain.model.LinkInsertion;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.PageDraft;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PagePath;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.PageWriteTask;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.RunFencedException;
import dev.mindforge.domain.model.RunProgress;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.SupersededSection;
import dev.mindforge.domain.model.SupersessionProposal;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.model.ValidationResult;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.WikiStore;

/**
 * Ingests one claimed {@code INGEST} run end to end (ADR 0013, T17): Preprocess → RelevanceGuard → Extract per chunk →
 * Resolve → parallel writes with draft checks → link check → commit 1 → Supersede → commit 2. Every model call happens
 * outside a transaction; each commit is fenced on the run's status and lease. A run fails only when no page task
 * succeeded; a failure after commit 1 completes it with {@code supersession_skipped}.
 */
public class IngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestPipeline.class);

    private static final int LINK_CHECK_PAGES_PER_CALL = 10;
    private static final String PLANNED_DESCRIPTION = "(nowa strona)";

    private final DocumentRepository documents;
    private final WikiStore wiki;
    private final IngestRunRepository runs;
    private final RelevanceGuard guard;
    private final ClaimExtractor extractor;
    private final PageWriter writer;
    private final LinkChecker linkChecker;
    private final SupersessionDetector detector;
    private final ProgressNotifier progress;
    private final TransactionOperations transactions;
    private final ProcessingSettings settings;

    public IngestPipeline(DocumentRepository documents, WikiStore wiki, IngestRunRepository runs,
                          RelevanceGuard guard, ClaimExtractor extractor, PageWriter writer, LinkChecker linkChecker,
                          SupersessionDetector detector, ProgressNotifier progress,
                          TransactionOperations transactions, ProcessingSettings settings) {
        this.documents = documents;
        this.wiki = wiki;
        this.runs = runs;
        this.guard = guard;
        this.extractor = extractor;
        this.writer = writer;
        this.linkChecker = linkChecker;
        this.detector = detector;
        this.progress = progress;
        this.transactions = transactions;
        this.settings = settings;
    }

    /** Runs a claimed ({@code RUNNING}) run to {@code COMPLETED} or {@code FAILED}, unless it is fenced. */
    public void run(IngestRun run) {
        RunState state = new RunState(run);
        String failure = "interrupted";
        boolean retryable = true;
        Committed committed = null;
        try {
            committed = generateAndCommit(state);
            failure = null;
        } catch (RunFencedException e) {
            log.warn("Run {} lost its lease before commit 1; nothing written: {}", run.runId(), e.getMessage());
            failure = null;
        } catch (IngestRunFailedException e) {
            failure = e.getMessage();
            retryable = e.retryable();
        } catch (RuntimeException e) {
            log.error("Run {} failed", run.runId(), e);
            failure = IngestRunFailedException.reasonFor(e);
        } finally {
            if (failure != null) {
                fail(state, failure, retryable);
            }
        }
        if (committed != null) {
            supersedeAndComplete(state, committed);
        }
    }

    // ---------------------------------------------------------------------------
    // Generation and commit 1
    // ---------------------------------------------------------------------------

    private Committed generateAndCommit(RunState state) {
        UUID kbId = state.kbId();
        Document document = documents.findById(kbId, state.run.documentId())
            .orElseThrow(() -> new IngestRunFailedException("the run's document is gone", false));
        boolean conversation = document.uploadSource() == UploadSource.CONVERSATION;
        boolean article = document.uploadSource() == UploadSource.ARTICLE;
        List<IndexEntry> index = wiki.listIndex(kbId);
        String renderedIndex = IndexRenderer.render(index);
        Resolver resolver = new Resolver(index, article);
        List<ContentBlock> blocks = Preprocessor.clean(document.contentBlocks());
        List<String> digests = new ArrayList<>();
        List<EditItem> edits = List.of();

        if (conversation) {
            step(state, "extract", null, null);
            ConversationTurn turn = ConversationTurn.parse(document.originalContent());
            edits = extractor.extractEdit(turn.instruction(), turn.quotedAnswer(), renderedIndex);
            state.version(ClaimExtractor.class, ClaimExtractor.VERSION, ClaimExtractor.TIER);
            if (edits.isEmpty()) {
                throw new IngestRunFailedException("edit named no page", false);
            }
            List<Claim> claims = edits.stream().filter(Claim.class::isInstance).map(Claim.class::cast).toList();
            checkClaimCap(claims.size());
            resolver.addClaims(claims);
        } else {
            List<List<ContentBlock>> chunks = HeadingChunker.split(blocks, settings.chunkSizeTokens());
            if (chunks.isEmpty()) {
                throw new IngestRunFailedException("the document has no text", false);
            }
            // ponytail: the guard reads the first chunk only; a document is judged by how it opens
            ValidationResult verdict = guard.check(document.lessonIdentity().title(), text(chunks.getFirst()));
            state.version(RelevanceGuard.class, RelevanceGuard.VERSION, RelevanceGuard.TIER);
            if (!verdict.passed()) {
                throw new IngestRunFailedException("not learning material: " + verdict.reason(), false);
            }
            step(state, "extract", null, null);
            for (List<ContentBlock> chunk : chunks) {
                ExtractResult result = extractor.extract(chunk, renderedIndex, resolver.planned());
                state.version(ClaimExtractor.class, ClaimExtractor.VERSION, ClaimExtractor.TIER);
                checkClaimCap(result.claims().size());
                resolver.addClaims(result.claims());
                digests.add(result.chunkDigest());
            }
        }

        step(state, "resolve", null, null);
        Resolver.Plan plan = resolver.finish(edits,
            conversation ? null : PagePath.sourceSummary(document.lessonIdentity().lessonId()),
            document.lessonIdentity().title(), settings.maxPageTasksPerRun());
        state.failures.addAll(plan.failures());

        Set<String> touched = new HashSet<>(plan.deletions());
        touched.addAll(plan.retitles().keySet());
        plan.tasks().stream().filter(task -> !task.create()).forEach(task -> touched.add(task.path()));
        Map<String, WikiPage> livePages = byPath(wiki.findByPaths(kbId, touched));

        String linkableIndex = linkableIndex(index, plan);
        Map<String, String> drafted = new ConcurrentHashMap<>();
        List<Drafted> writes = writeAll(state, document, conversation, blocks, digests, plan, livePages,
            linkableIndex, drafted);
        int succeeded = drafted.size() + plan.deletions().size();
        for (Map.Entry<String, String> retitle : plan.retitles().entrySet()) {
            WikiPage page = livePages.get(retitle.getKey());
            succeeded++;
            if (!page.title().equals(retitle.getValue())) {
                writes.add(new Drafted(new PageWrite(page.pageId(), page.path(), retitle.getValue(),
                    page.description(), page.type(), page.markdownBody()), List.of(), false));
            }
        }
        if (succeeded == 0) {
            // a conversation edit that changed nothing would replay the same instruction on retry
            throw new IngestRunFailedException(conversation ? "no applicable change" : "no page task succeeded",
                !conversation);
        }

        List<Drafted> checked = linkCheck(state, writes, plan.deletions(), drafted, linkableIndex);

        List<PageRevision> revisions = transactions.execute(status -> {
            runs.markWritten(kbId, state.run.runId(), state.failures(), state.versions());
            List<PageRevision> written = new ArrayList<>();
            for (Drafted write : checked) {
                written.add(wiki.savePage(kbId, write.write(), state.run.runId()));
            }
            wiki.addSources(kbId, checked.stream()
                .map(write -> new PageSource(write.write().pageId(), document.documentId(), state.run.runId()))
                .toList());
            for (String path : plan.deletions()) {
                written.add(wiki.deletePage(kbId, livePages.get(path).pageId(), state.run.runId()));
            }
            return written;
        });
        return new Committed(article, checked, revisions);
    }

    private void checkClaimCap(int claims) {
        if (claims > settings.maxClaimsPerExtractCall()) {
            throw new IngestRunFailedException("Extract returned " + claims + " claims (limit "
                + settings.maxClaimsPerExtractCall() + ")", true);
        }
    }

    /** Drafts every task in parallel; a successful draft's body lands in {@code drafted}, a changed one is returned. */
    private List<Drafted> writeAll(RunState state, Document document, boolean conversation, List<ContentBlock> blocks,
                                   List<String> digests, Resolver.Plan plan, Map<String, WikiPage> livePages,
                                   String linkableIndex, Map<String, String> drafted) {
        List<PageWriteTask> tasks = plan.tasks();
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        step(state, "write", 0, tasks.size());
        Map<UUID, List<LiveSupersession>> supersededByPage = wiki.liveSupersessionsOf(state.kbId(),
                livePages.values().stream().map(WikiPage::pageId).toList()).stream()
            .collect(Collectors.groupingBy(LiveSupersession::supersededPageId));
        String wholeDocument = text(blocks);
        String summarySource = TokenEstimate.of(wholeDocument) <= settings.chunkSizeTokens()
            ? wholeDocument
            : String.join("\n\n", digests);
        AtomicInteger done = new AtomicInteger();

        List<Drafted> changed = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (PageWriteTask task : tasks) {
                futures.add(executor.submit(() -> {
                    try {
                        WikiPage existing = livePages.get(task.path());
                        String source = task.type().equals(PageType.SOURCE_SUMMARY)
                            ? summarySource
                            : sourceBlocks(blocks, task.claims());
                        Optional<Drafted> result = draft(state, task, existing, source, supersededByPage,
                            linkableIndex, !conversation && existing != null && task.type().equals(PageType.CONCEPT));
                        result.ifPresent(changed::add);
                        // an unchanged draft is only possible for a live page, whose body stays
                        drafted.put(task.path(),
                            result.map(write -> write.write().markdownBody()).orElseGet(() -> existing.markdownBody()));
                    } catch (TaskFailed e) {
                        state.failures.add(Map.of("step", "write", "path", task.path(), "reason", e.getMessage()));
                    } finally {
                        step(state, "write", done.incrementAndGet(), tasks.size());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while writing pages", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("a page write crashed", e.getCause());
        }
        return new ArrayList<>(changed);
    }

    /** The changed write, empty for a draft identical to the live page; {@link TaskFailed} when the task fails. */
    private Optional<Drafted> draft(RunState state, PageWriteTask task, WikiPage existing, String source,
                                             Map<UUID, List<LiveSupersession>> supersededByPage,
                                             String linkableIndex, boolean keepSections) {
        if (task.create() && !Resolver.isValidTitle(task.title())) {
            throw new TaskFailed("invalid title");
        }
        String existingBody = existing == null ? null : existing.markdownBody();
        List<SupersededSection> superseded = existing == null ? List.of() : supersededSections(existing,
            supersededByPage.getOrDefault(existing.pageId(), List.of()));
        PageDraft draft;
        try {
            draft = DraftValidator.normalise(
                writer.write(task, source, existingBody, superseded, linkableIndex, keepSections));
        } catch (RuntimeException e) {
            log.warn("Writing {} in run {} failed", task.path(), state.run.runId(), e);
            throw new TaskFailed(IngestRunFailedException.reasonFor(e));
        } finally {
            state.version(PageWriter.class, PageWriter.VERSION, PageWriter.TIER);
        }
        DraftValidator.problem(task.title(), draft, existingBody, keepSections).ifPresent(problem -> {
            throw new TaskFailed(problem);
        });
        if (existing != null && existing.title().equals(task.title())
            && existing.description().equals(draft.description()) && existingBody.equals(draft.body())) {
            return Optional.empty();
        }
        UUID pageId = existing == null ? UUID.randomUUID() : existing.pageId();
        return Optional.of(new Drafted(new PageWrite(pageId, task.path(), task.title(), draft.description(),
            task.type(), draft.body()), task.claims(), true));
    }

    /** Proposes links over this run's drafted bodies and applies the verified ones; a failure keeps the bodies. */
    private List<Drafted> linkCheck(RunState state, List<Drafted> writes, Set<String> deletions,
                                    Map<String, String> drafted, String linkableIndex) {
        List<Drafted> checkable = writes.stream().filter(Drafted::drafted).toList();
        if (checkable.isEmpty()) {
            return writes;
        }
        step(state, "linkCheck", null, null);
        try {
            List<LinkInsertion> proposals = new ArrayList<>();
            for (int i = 0; i < checkable.size(); i += LINK_CHECK_PAGES_PER_CALL) {
                Map<String, String> batch = new LinkedHashMap<>();
                checkable.subList(i, Math.min(i + LINK_CHECK_PAGES_PER_CALL, checkable.size()))
                    .forEach(write -> batch.put(write.write().path(), write.write().markdownBody()));
                proposals.addAll(linkChecker.check(batch, linkableIndex));
                state.version(LinkChecker.class, LinkChecker.VERSION, LinkChecker.TIER);
            }
            Map<String, String> targets = new HashMap<>();
            Set<String> liveTargets = proposals.stream().map(LinkInsertion::targetPath).filter(Objects::nonNull)
                .filter(path -> !drafted.containsKey(path) && !deletions.contains(path))
                .collect(Collectors.toSet());
            wiki.findByPaths(state.kbId(), liveTargets).forEach(page -> targets.put(page.path(), page.markdownBody()));
            targets.putAll(drafted);

            Map<String, List<LinkInsertion>> byPage = proposals.stream()
                .collect(Collectors.groupingBy(proposal -> String.valueOf(proposal.pagePath())));
            int dropped = 0;
            List<Drafted> linked = new ArrayList<>();
            for (Drafted write : writes) {
                List<LinkInsertion> forPage = write.drafted()
                    ? byPage.getOrDefault(write.write().path(), List.of())
                    : List.of();
                LinkInsertionApplier.Result result = LinkInsertionApplier.apply(write.write().path(),
                    write.write().markdownBody(), forPage, targets);
                dropped += result.dropped();
                linked.add(write.withBody(result.body()));
            }
            Set<String> checkedPaths = checkable.stream().map(write -> write.write().path()).collect(Collectors.toSet());
            dropped += (int) proposals.stream().filter(proposal -> !checkedPaths.contains(proposal.pagePath())).count();
            if (dropped > 0) {
                state.failures.add(Map.of("step", "linkCheck", "dropped", dropped));
            }
            return linked;
        } catch (RuntimeException e) {
            log.warn("Link check of run {} failed; committing without extra links", state.run.runId(), e);
            state.failures.add(Map.of("step", "linkCheck", "reason",
                IngestRunFailedException.reasonFor(e)));
            return writes;
        }
    }

    // ---------------------------------------------------------------------------
    // Supersede and commit 2
    // ---------------------------------------------------------------------------

    private void supersedeAndComplete(RunState state, Committed committed) {
        UUID kbId = state.kbId();
        try {
            progress.notify(kbId, RunProgress.status(state.run, RunStatus.WRITTEN));
            if (committed.article()) {
                state.failures.add(Map.of("step", "supersede", "reason", "article"));
                complete(state, List.of(), true);
                return;
            }
            if (committed.claims().isEmpty()) {
                complete(state, List.of(), false);
                return;
            }
            step(state, "supersede", null, null);
            List<PageSupersession> rows = supersessions(state, committed);
            complete(state, rows, false);
        } catch (RunFencedException e) {
            log.warn("Run {} lost its lease before commit 2: {}", state.run.runId(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Supersede of run {} failed; completing without supersessions", state.run.runId(), e);
            state.failures.add(Map.of("step", "supersede", "reason",
                IngestRunFailedException.reasonFor(e)));
            try {
                complete(state, List.of(), true);
            } catch (RunFencedException fenced) {
                log.warn("Run {} lost its lease before completing: {}", state.run.runId(), fenced.getMessage());
            }
        }
    }

    /** Candidate sections are read after commit 1: Concepts this run wrote and those one link away from them. */
    private List<PageSupersession> supersessions(RunState state, Committed committed) {
        UUID kbId = state.kbId();
        Set<UUID> writtenIds = committed.revised().stream().map(PageWrite::pageId).collect(Collectors.toSet());
        Set<String> writtenPaths = committed.revised().stream().map(PageWrite::path).collect(Collectors.toSet());
        List<PageLink> outbound = wiki.outboundLinks(kbId, writtenIds);
        List<PageLink> inbound = wiki.inboundLinks(kbId, writtenPaths);

        Set<String> paths = new HashSet<>(writtenPaths);
        outbound.forEach(link -> paths.add(link.targetPath()));
        Map<UUID, WikiPage> candidates = new LinkedHashMap<>();
        wiki.findByPaths(kbId, paths).forEach(page -> candidates.put(page.pageId(), page));
        inbound.stream().map(PageLink::pageId).distinct().filter(id -> !candidates.containsKey(id))
            .forEach(id -> wiki.findById(kbId, id).ifPresent(page -> candidates.put(id, page)));
        candidates.values().removeIf(page -> !page.type().equals(PageType.CONCEPT));

        Map<String, Integer> linkCounts = new HashMap<>();
        outbound.forEach(link -> linkCounts.merge(link.targetPath(), 1, Integer::sum));
        inbound.forEach(link -> {
            WikiPage source = candidates.get(link.pageId());
            if (source != null) {
                linkCounts.merge(source.path(), 1, Integer::sum);
            }
        });
        Set<String> superseded = wiki.liveSupersessionsOf(kbId, candidates.keySet()).stream()
            .map(row -> SupersessionInputs.key(candidates.get(row.supersededPageId()).path(), row.sectionAnchor()))
            .collect(Collectors.toSet());

        SupersessionInputs.Selection selection = SupersessionInputs.select(List.copyOf(candidates.values()),
            linkCounts, superseded, settings.supersessionContextTokens());
        if (selection.omittedPages() > 0) {
            state.failures.add(Map.of("step", "supersede", "omittedPages", selection.omittedPages()));
        }
        if (selection.sections().isEmpty()) {
            return List.of();
        }
        List<SupersessionProposal> proposals = detector.detect(committed.claims(), selection.sections());
        state.version(SupersessionDetector.class, SupersessionDetector.VERSION, SupersessionDetector.TIER);
        List<SupersessionProposal> kept = SupersessionInputs.verify(proposals, selection.sections(), writtenPaths);
        if (kept.size() < proposals.size()) {
            state.failures.add(Map.of("step", "supersede", "dropped", proposals.size() - kept.size()));
        }
        Map<String, UUID> ids = new HashMap<>();
        candidates.values().forEach(page -> ids.put(page.path(), page.pageId()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return kept.stream()
            .map(proposal -> new PageSupersession(UUID.randomUUID(), ids.get(proposal.supersededPath()),
                proposal.sectionAnchor(), ids.get(proposal.supersedingPath()), state.run.runId(), now))
            .toList();
    }

    private void complete(RunState state, List<PageSupersession> rows, boolean skipped) {
        transactions.executeWithoutResult(status -> {
            runs.complete(state.kbId(), state.run.runId(), rows.size(), skipped, state.failures(), state.versions());
            if (!rows.isEmpty()) {
                wiki.addSupersessions(state.kbId(), rows);
            }
        });
        progress.notify(state.kbId(), RunProgress.status(state.run, RunStatus.COMPLETED));
    }

    private void fail(RunState state, String reason, boolean retryable) {
        try {
            runs.fail(state.kbId(), state.run.runId(), reason, retryable, state.failures(), state.versions());
            progress.notify(state.kbId(), RunProgress.status(state.run, RunStatus.FAILED));
        } catch (RunFencedException e) {
            log.warn("Run {} lost its lease before failing: {}", state.run.runId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not record the failure of run {}; the sweep settles it", state.run.runId(), e);
        }
    }

    // ---------------------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------------------

    /** Live pages minus this run's deletions, plus its planned creates. */
    private static String linkableIndex(List<IndexEntry> index, Resolver.Plan plan) {
        List<IndexEntry> entries = new ArrayList<>(index.stream()
            .filter(entry -> !plan.deletions().contains(entry.path())).toList());
        plan.tasks().stream().filter(PageWriteTask::create)
            .forEach(task -> entries.add(new IndexEntry(task.path(), task.title(), PLANNED_DESCRIPTION, task.type())));
        return IndexRenderer.render(entries);
    }

    /** The blocks a task's claims came from, de-duplicated in document order, trimmed from the end to the budget. */
    private String sourceBlocks(List<ContentBlock> blocks, List<Claim> claims) {
        List<ContentBlock> chosen = blocks.stream()
            .filter(block -> claims.stream().anyMatch(claim -> claim.firstBlock() != Claim.NO_BLOCKS
                && block.position() >= claim.firstBlock() && block.position() <= claim.lastBlock()))
            .toList();
        List<ContentBlock> kept = new ArrayList<>();
        int tokens = 0;
        for (ContentBlock block : chosen) {
            tokens += TokenEstimate.of(block.content());
            if (tokens > settings.writerSourceTokens()) {
                break;
            }
            kept.add(block);
        }
        return text(kept);
    }

    private static List<SupersededSection> supersededSections(WikiPage page, List<LiveSupersession> rows) {
        return rows.stream()
            .flatMap(row -> MarkdownStructure.section(page.markdownBody(), row.sectionAnchor()).stream()
                .map(section -> new SupersededSection(section.heading(), section.anchor(), row.supersedingPath(),
                    row.supersedingTitle())))
            .toList();
    }

    private static String text(List<ContentBlock> blocks) {
        return blocks.stream().map(ContentBlock::content).collect(Collectors.joining("\n\n"));
    }

    private static Map<String, WikiPage> byPath(Collection<WikiPage> pages) {
        return pages.stream().collect(Collectors.toMap(WikiPage::path, Function.identity()));
    }

    private void step(RunState state, String step, Integer done, Integer total) {
        progress.notify(state.kbId(), RunProgress.step(state.run, step, done, total));
    }

    // ---------------------------------------------------------------------------
    // Run state
    // ---------------------------------------------------------------------------

    /** What a run accumulates while it generates: failures and the version of every model service it called. */
    private final class RunState {

        private final IngestRun run;
        private final List<Map<String, Object>> failures = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, String> versions = new ConcurrentHashMap<>();

        private RunState(IngestRun run) {
            this.run = run;
        }

        private UUID kbId() {
            return run.knowledgeBaseId();
        }

        private void version(Class<?> service, String version, ModelTier tier) {
            versions.put(service.getSimpleName(), settings.stepVersion(version, tier));
        }

        private List<Map<String, Object>> failures() {
            synchronized (failures) {
                return List.copyOf(failures);
            }
        }

        private Map<String, String> versions() {
            return Map.copyOf(versions);
        }
    }

    /** A write to commit; {@code drafted} is false for a retitle that kept the body. */
    private record Drafted(PageWrite write, List<Claim> claims, boolean drafted) {

        private Drafted withBody(String body) {
            PageWrite w = write;
            return new Drafted(new PageWrite(w.pageId(), w.path(), w.title(), w.description(), w.type(), body),
                claims, drafted);
        }
    }

    /** What commit 1 wrote; everything read from it is derived after, inside Supersede's failure handling. */
    private record Committed(boolean article, List<Drafted> checked, List<PageRevision> revisions) {

        private List<PageWrite> revised() {
            return revisedConcepts().map(Drafted::write).toList();
        }

        private List<Claim> claims() {
            return revisedConcepts().flatMap(write -> write.claims().stream()).toList();
        }

        private Stream<Drafted> revisedConcepts() {
            Set<UUID> revised = revisions.stream().filter(revision -> !revision.isTombstone())
                .map(PageRevision::pageId).collect(Collectors.toSet());
            return checked.stream().filter(write -> write.write().type().equals(PageType.CONCEPT)
                && revised.contains(write.write().pageId()));
        }
    }

    private static final class TaskFailed extends IllegalStateException {

        private TaskFailed(String reason) {
            super(reason);
        }
    }
}
