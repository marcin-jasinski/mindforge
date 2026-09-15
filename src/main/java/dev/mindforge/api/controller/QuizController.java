package dev.mindforge.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.request.AnswerRequest;
import dev.mindforge.api.dto.request.QuizSessionRequest;
import dev.mindforge.api.dto.response.EvaluationResponse;
import dev.mindforge.api.dto.response.NextQuestionResponse;
import dev.mindforge.api.dto.response.QuizSessionResponse;
import dev.mindforge.api.mapper.StudyDtoMapper;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QuizService;

/** Quizzes whose reference answers stay on the server: a response carries a question, a score and feedback only. */
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/quiz-sessions")
public class QuizController {

    private final KnowledgeBaseService knowledgeBases;
    private final QuizService quizzes;
    private final StudyDtoMapper mapper;

    public QuizController(KnowledgeBaseService knowledgeBases, QuizService quizzes, StudyDtoMapper mapper) {
        this.knowledgeBases = knowledgeBases;
        this.quizzes = quizzes;
        this.mapper = mapper;
    }

    @Operation(summary = "Start a quiz over a study scope")
    @ApiResponse(responseCode = "201", description = "Started")
    @ApiResponse(responseCode = "404", description = "Nothing to ask about in the scope")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizSessionResponse start(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                     @RequestBody QuizSessionRequest request) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(quizzes.start(kbId, user.userId(), StudyScopes.of(request.lessonId(), request.pageId())));
    }

    @Operation(summary = "Get the next question's text")
    @ApiResponse(responseCode = "204", description = "The quiz is over")
    @GetMapping("/{sessionId}/next")
    public ResponseEntity<NextQuestionResponse> next(@AuthenticationPrincipal CurrentUser user,
                                                     @PathVariable UUID kbId, @PathVariable UUID sessionId) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return quizzes.next(kbId, user.userId(), sessionId).map(mapper::toResponse).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Answer the current question and get it graded")
    @ApiResponse(responseCode = "409", description = "The quiz is over")
    @PostMapping("/{sessionId}/answers")
    public EvaluationResponse answer(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kbId,
                                     @PathVariable UUID sessionId, @Valid @RequestBody AnswerRequest request) {
        knowledgeBases.requireOwner(kbId, user.userId());
        return mapper.toResponse(quizzes.answer(kbId, user.userId(), sessionId, request.answer()));
    }
}
