package dev.mindforge.api.mapper;

import java.util.List;
import java.util.Map;

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
import dev.mindforge.domain.model.RunSummary;
import dev.mindforge.domain.model.RunSupersession;

/** Runs as the SPA shows them. Step versions are never mapped, and failures pass through a fixed set of keys. */
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

    static List<FindingResponse> toFindings(List<Map<String, Object>> findings) {
        return findings.stream()
            .map(finding -> new FindingResponse(text(finding, "kind"),
                finding.get("pages") instanceof List<?> pages ? pages.stream().map(String::valueOf).toList() : List.of(),
                text(finding, "text")))
            .toList();
    }

    private static FailureResponse toFailure(Map<String, Object> failure) {
        Object count = failure.containsKey("dropped") ? failure.get("dropped") : failure.get("omittedPages");
        return new FailureResponse(text(failure, "step"), text(failure, "item"), text(failure, "path"),
            text(failure, "reason"), count instanceof Number number ? number.intValue() : null);
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
