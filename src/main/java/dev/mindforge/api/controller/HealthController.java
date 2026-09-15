package dev.mindforge.api.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.response.HealthResponse;
import dev.mindforge.api.dto.response.RunAcceptedResponse;
import dev.mindforge.api.mapper.HealthDtoMapper;
import dev.mindforge.application.service.HealthService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.LintService;
import dev.mindforge.application.service.RunReportService;

@RestController
@RequestMapping("/api/knowledge-bases/{kbId}")
public class HealthController {

    private final KnowledgeBaseService knowledgeBases;
    private final HealthService health;
    private final RunReportService reports;
    private final LintService lint;
    private final HealthDtoMapper mapper;

    public HealthController(KnowledgeBaseService knowledgeBases, HealthService health, RunReportService reports,
                            LintService lint, HealthDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.health = health;
        this.reports = reports;
        this.lint = lint;
        this.mapper = mapper;
    }

    @Operation(summary = "Get the live health checks and the latest full review")
    @GetMapping("/health")
    public HealthResponse health(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(health.health(kbId), reports.latestReview(kbId));
    }

    @Operation(summary = "Start a full review")
    @ApiResponse(responseCode = "202", description = "Queued")
    @ApiResponse(responseCode = "409", description = "A full review is already queued or running")
    @PostMapping("/lint-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunAcceptedResponse startLint(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return new RunAcceptedResponse(lint.request(kbId));
    }
}
