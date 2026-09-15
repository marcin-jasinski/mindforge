package dev.mindforge.api.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.response.GraphResponse;
import dev.mindforge.api.dto.response.IndexResponse;
import dev.mindforge.api.dto.response.PageResponse;
import dev.mindforge.api.dto.response.RevisionResponse;
import dev.mindforge.api.mapper.WikiDtoMapper;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.WikiService;

/** The wiki as the SPA browses it. A page path is its two segments, {@code concepts/mitoza}. */
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}")
public class WikiController {

    private final KnowledgeBaseService knowledgeBases;
    private final WikiService wiki;
    private final WikiDtoMapper mapper;

    public WikiController(KnowledgeBaseService knowledgeBases, WikiService wiki, WikiDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.wiki = wiki;
        this.mapper = mapper;
    }

    @Operation(summary = "Get the rendered index and its entries")
    @GetMapping("/index")
    public IndexResponse index(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(wiki.index(kbId));
    }

    @Operation(summary = "Get a page rendered with its supersession notes and citations")
    @ApiResponse(responseCode = "404", description = "No live page at the path")
    @GetMapping("/pages/{directory}/{name}")
    public PageResponse page(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                             @PathVariable String directory, @PathVariable String name) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(wiki.page(kbId, directory + "/" + name));
    }

    @Operation(summary = "List a page's revisions, oldest first, so each follows its prior revision")
    @GetMapping("/pages/{directory}/{name}/revisions")
    public List<RevisionResponse> revisions(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                            @PathVariable String directory, @PathVariable String name) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return wiki.revisions(kbId, directory + "/" + name).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Get the live pages and the links between them")
    @GetMapping("/graph")
    public GraphResponse graph(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(wiki.graph(kbId));
    }
}
