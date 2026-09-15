package dev.mindforge.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.request.KnowledgeBaseRequest;
import dev.mindforge.api.dto.response.KnowledgeBaseResponse;
import dev.mindforge.api.mapper.KnowledgeBaseDtoMapper;
import dev.mindforge.application.service.KnowledgeBaseService;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBases;
    private final KnowledgeBaseDtoMapper mapper;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBases, KnowledgeBaseDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.mapper = mapper;
    }

    @Operation(summary = "List the signed-in user's knowledge bases")
    @GetMapping
    public List<KnowledgeBaseResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return knowledgeBases.list(user.userId()).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Create a knowledge base")
    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeBaseResponse create(@AuthenticationPrincipal CurrentUser user,
                                        @Valid @RequestBody KnowledgeBaseRequest request) {
        return mapper.toResponse(knowledgeBases.create(user.userId(), request.name(), request.description()));
    }

    @Operation(summary = "Get a knowledge base with its document and page counts")
    @ApiResponse(responseCode = "403", description = "Not the owner")
    @ApiResponse(responseCode = "404", description = "No such knowledge base")
    @GetMapping("/{kbId}")
    public KnowledgeBaseResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        return mapper.toResponse(knowledgeBases.get(kbId, user.userId()));
    }

    @Operation(summary = "Rename or re-describe a knowledge base")
    @PatchMapping("/{kbId}")
    public KnowledgeBaseResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                        @Valid @RequestBody KnowledgeBaseRequest request) {
        return mapper.toResponse(knowledgeBases.update(kbId, user.userId(), request.name(), request.description()));
    }

    @Operation(summary = "Delete a knowledge base and everything in it")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "409", description = "A run is active")
    @DeleteMapping("/{kbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.delete(kbId, user.userId());
    }
}
