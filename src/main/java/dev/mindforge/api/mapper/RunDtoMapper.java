package dev.mindforge.api.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import dev.mindforge.api.dto.response.FailureResponse;
import dev.mindforge.api.dto.response.FindingResponse;
import dev.mindforge.api.dto.response.PageChangeResponse;
import dev.mindforge.api.dto.response.RunReportResponse;
import dev.mindforge.api.dto.response.RunSummaryResponse;
import dev.mindforge.api.dto.response.RunSupersessionResponse;
import dev.mindforge.application.service.RunReportService;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.ReviewItem;
import dev.mindforge.domain.model.RunFailure;
import dev.mindforge.domain.model.RunSummary;
import dev.mindforge.domain.model.RunSupersession;

/** Runs as the SPA shows them. Step versions are never mapped. */
@Mapper(componentModel = "spring", uses = WikiDtoMapper.class)
public interface RunDtoMapper {

    RunSummaryResponse toResponse(RunSummary summary);

    RunSupersessionResponse toResponse(RunSupersession supersession);

    default RunReportResponse toResponse(RunReportService.Report report, WikiDtoMapper wiki) {
        IngestRun run = report.run();
        return new RunReportResponse(run.runId(), run.kind(), run.status(), run.documentId(), run.attempt(),
            run.retryable(), run.failureReason(), run.supersessionCount(), run.supersessionSkipped(), run.createdAt(),
            run.startedAt(), run.finishedAt(),
            report.pages().stream().map(change -> toResponse(change, wiki)).toList(),
            report.supersessions().stream().map(this::toResponse).toList(),
            run.failures().stream().map(RunDtoMapper::toFailure).toList(),
            toFindings(run.findings()), report.revertOffered());
    }

    default PageChangeResponse toResponse(RunReportService.PageChange change, WikiDtoMapper wiki) {
        PageRevision after = change.after();
        PageRevision before = change.before();
        return new PageChangeResponse(after.pageId(), after.path(), wiki.toResponse(after),
            before == null ? null : wiki.toResponse(before),
            before == null || before.isTombstone() ? null : before.markdownBody().length(),
            after.isTombstone() ? null : after.markdownBody().length());
    }

    static List<FindingResponse> toFindings(List<ReviewItem> findings) {
        return findings.stream().map(finding -> new FindingResponse(finding.kind(), finding.pages(), finding.text()))
            .toList();
    }

    private static FailureResponse toFailure(RunFailure failure) {
        return new FailureResponse(failure.step(), failure.item(), failure.path(), failure.reason(),
            failure.dropped() != null ? failure.dropped() : failure.omittedPages());
    }
}
