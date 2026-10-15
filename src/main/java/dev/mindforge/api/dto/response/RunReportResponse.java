package dev.mindforge.api.dto.response;

import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What a run did: its pages with diffs, supersessions, failures, findings, and whether revert is offered. */
public record RunReportResponse(
    UUID runId,
    RunKind kind,
    RunStatus status,
    UUID documentId,
    int attempt,
    Boolean retryable,
    String failureReason,
    int supersessionCount,
    boolean supersessionSkipped,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    List<PageChangeResponse> pages,
    List<RunSupersessionResponse> supersessions,
    List<FailureResponse> failures,
    List<FindingResponse> findings,
    boolean revertOffered
) {}
