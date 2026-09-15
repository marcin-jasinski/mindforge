package dev.mindforge.api.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.response.IndexEntryResponse;
import dev.mindforge.api.mapper.WikiDtoMapper;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.SearchService;

@RestController
public class SearchController {

    private final KnowledgeBaseService knowledgeBases;
    private final SearchService search;
    private final WikiDtoMapper mapper;

    public SearchController(KnowledgeBaseService knowledgeBases, SearchService search, WikiDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.search = search;
        this.mapper = mapper;
    }

    @Operation(summary = "Search pages by title, description and body")
    @GetMapping("/api/knowledge-bases/{kbId}/pages/search")
    public List<IndexEntryResponse> search(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                           @RequestParam String q) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return search.search(kbId, q).stream().map(mapper::toResponse).toList();
    }
}
