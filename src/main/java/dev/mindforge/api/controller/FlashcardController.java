package dev.mindforge.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.request.ReviewRequest;
import dev.mindforge.api.dto.response.FlashcardResponse;
import dev.mindforge.api.mapper.StudyDtoMapper;
import dev.mindforge.application.service.FlashcardService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.ReviewResult;

@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/flashcards")
public class FlashcardController {

    private final KnowledgeBaseService knowledgeBases;
    private final FlashcardService flashcards;
    private final StudyDtoMapper mapper;

    public FlashcardController(KnowledgeBaseService knowledgeBases, FlashcardService flashcards,
                               StudyDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.flashcards = flashcards;
        this.mapper = mapper;
    }

    @Operation(summary = "Open a deck: refresh the scope's cards within the session budget and list the due ones")
    @ApiResponse(responseCode = "404", description = "No such lesson or page")
    @GetMapping
    public List<FlashcardResponse> deck(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                        @RequestParam(required = false) String lessonId,
                                        @RequestParam(required = false) UUID pageId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return flashcards.deck(kbId, StudyScopes.of(lessonId, pageId)).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Rate a card and reschedule it")
    @PostMapping("/{cardId}/reviews")
    public FlashcardResponse review(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                    @PathVariable String cardId, @Valid @RequestBody ReviewRequest request) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(flashcards.review(kbId, cardId, new ReviewResult(request.rating())));
    }
}
