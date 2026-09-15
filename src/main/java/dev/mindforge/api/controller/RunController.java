package dev.mindforge.api.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.response.RunAcceptedResponse;
import dev.mindforge.api.dto.response.RunReportResponse;
import dev.mindforge.api.dto.response.RunSummaryResponse;
import dev.mindforge.api.mapper.RunDtoMapper;
import dev.mindforge.api.mapper.WikiDtoMapper;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.RevertService;
import dev.mindforge.application.service.RunReportService;
import dev.mindforge.infrastructure.event.SseProgressNotifier;

@RestController
@RequestMapping("/api/knowledge-bases/{kbId}")
public class RunController {

    private final KnowledgeBaseService knowledgeBases;
    private final RunReportService reports;
    private final RevertService revertService;
    private final SseProgressNotifier progress;
    private final RunDtoMapper mapper;
    private final WikiDtoMapper wikiMapper;

    public RunController(KnowledgeBaseService knowledgeBases, RunReportService reports, RevertService revertService,
                         SseProgressNotifier progress, RunDtoMapper mapper, WikiDtoMapper wikiMapper) {
        this.knowledgeBases = knowledgeBases;
        this.reports = reports;
        this.revertService = revertService;
        this.progress = progress;
        this.mapper = mapper;
        this.wikiMapper = wikiMapper;
    }

    @Operation(summary = "List the knowledge base's runs, newest first")
    @GetMapping("/runs")
    public List<RunSummaryResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return reports.list(kbId).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Get a run's report")
    @ApiResponse(responseCode = "404", description = "No such run")
    @GetMapping("/runs/{runId}")
    public RunReportResponse report(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                    @PathVariable UUID runId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(reports.report(kbId, runId), wikiMapper);
    }

    @Operation(summary = "Revert a run, synchronously")
    @ApiResponse(responseCode = "409", description = "A run is active, or revert is not offered")
    @PostMapping("/runs/{runId}/revert")
    public RunAcceptedResponse revert(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                      @PathVariable UUID runId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return new RunAcceptedResponse(revertService.revert(kbId, runId));
    }

    @Operation(summary = "Remove one supersession, synchronously")
    @ApiResponse(responseCode = "409", description = "A run is active, or there is no such supersession")
    @DeleteMapping("/supersessions/{supersessionId}")
    public RunAcceptedResponse removeSupersession(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                                  @PathVariable UUID supersessionId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return new RunAcceptedResponse(revertService.removeSupersession(kbId, supersessionId));
    }

    @Operation(summary = "Stream progress of every run of the knowledge base over SSE")
    @GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return progress.subscribe(kbId);
    }
}
