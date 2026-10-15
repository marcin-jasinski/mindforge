package dev.mindforge.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunSummary;
import dev.mindforge.domain.model.RunSupersession;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.WikiStore;

/** The run list and a run's report: what it wrote beside what each page was before, and whether revert is offered. */
public class RunReportService {

    private static final int RUN_LIST_LIMIT = 100;

    /** One page a run wrote: the revision it appended and the one before, null for a page it created. */
    public record PageChange(PageRevision after, PageRevision before) {}

    public record Report(IngestRun run, List<PageChange> pages, List<RunSupersession> supersessions,
                         boolean revertOffered) {}

    private final RunReportQuery reports;
    private final IngestRunRepository runs;
    private final WikiStore wiki;
    private final RevertService revertService;

    public RunReportService(RunReportQuery reports, IngestRunRepository runs, WikiStore wiki,
                            RevertService revertService) {
        this.reports = reports;
        this.runs = runs;
        this.wiki = wiki;
        this.revertService = revertService;
    }

    public List<RunSummary> list(UUID kbId) {
        return reports.listRuns(kbId, RUN_LIST_LIMIT);
    }

    /** @throws NotFoundException when the knowledge base has no such run */
    public Report report(UUID kbId, UUID runId) {
        IngestRun run = runs.findById(kbId, runId).orElseThrow(() -> new NotFoundException("Run"));
        List<PageChange> pages = wiki.revisionsByRun(kbId, runId).stream()
            .map(after -> new PageChange(after, after.revision() == 1
                ? null
                : wiki.findRevision(kbId, after.pageId(), after.revision() - 1).orElse(null)))
            .toList();
        return new Report(run, pages, reports.supersessionsOf(kbId, runId), revertService.isOffered(kbId, runId));
    }

    /** The newest completed full Lint, whose findings and suggestions the health view shows. */
    public Optional<IngestRun> latestReview(UUID kbId) {
        return runs.latestCompleted(kbId, RunKind.LINT);
    }
}
