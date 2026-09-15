package dev.mindforge.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.request.EditRequest;
import dev.mindforge.api.dto.request.QuestionRequest;
import dev.mindforge.api.dto.response.AnswerResponse;
import dev.mindforge.api.dto.response.QuerySessionResponse;
import dev.mindforge.api.dto.response.RunAcceptedResponse;
import dev.mindforge.api.dto.response.TurnSummaryResponse;
import dev.mindforge.api.mapper.QueryDtoMapper;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QueryService;

/** Chat over the wiki: questions are answered read-only; an explicit edit is queued as an ingest run. */
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/query-sessions")
public class QueryController {

    private final KnowledgeBaseService knowledgeBases;
    private final QueryService query;
    private final IngestionService ingestion;
    private final QueryDtoMapper mapper;

    public QueryController(KnowledgeBaseService knowledgeBases, QueryService query, IngestionService ingestion,
                           QueryDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.query = query;
        this.ingestion = ingestion;
        this.mapper = mapper;
    }

    @Operation(summary = "Start a Query conversation")
    @ApiResponse(responseCode = "201", description = "Started")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuerySessionResponse start(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(query.start(kbId, user.userId()));
    }

    @Operation(summary = "List the signed-in user's past questions and answers, newest first")
    @GetMapping
    public List<TurnSummaryResponse> history(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return query.history(kbId, user.userId()).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Ask a question, answered from the wiki with page citations")
    @ApiResponse(responseCode = "404", description = "No such conversation")
    @PostMapping("/{interactionId}/messages")
    public AnswerResponse ask(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                              @PathVariable UUID interactionId, @Valid @RequestBody QuestionRequest request) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(query.ask(kbId, user.userId(), interactionId, request.question()));
    }

    @Operation(summary = "Change the wiki from chat; the edit runs as a queued ingest run")
    @ApiResponse(responseCode = "202", description = "Queued; follow the run's report")
    @PostMapping("/{interactionId}/edits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunAcceptedResponse edit(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                    @PathVariable UUID interactionId, @Valid @RequestBody EditRequest request) {
        knowledgeBases.requireOwner(kbId, user.userId());
        query.requireSession(kbId, user.userId(), interactionId);
        return new RunAcceptedResponse(ingestion.submitEdit(kbId, user.userId(), request.instruction(),
            request.quotedAnswer()));
    }
}
