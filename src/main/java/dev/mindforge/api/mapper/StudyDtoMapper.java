package dev.mindforge.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.mindforge.api.dto.response.EvaluationResponse;
import dev.mindforge.api.dto.response.FlashcardResponse;
import dev.mindforge.api.dto.response.NextQuestionResponse;
import dev.mindforge.api.dto.response.QuizSessionResponse;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.QuizSession;

@Mapper(componentModel = "spring")
public interface StudyDtoMapper {

    @Mapping(target = "cardId", source = "card.cardId")
    @Mapping(target = "pageId", source = "card.pageId")
    @Mapping(target = "sectionAnchor", source = "card.sectionAnchor")
    @Mapping(target = "cardType", source = "card.cardType")
    @Mapping(target = "front", source = "card.front")
    @Mapping(target = "back", source = "card.back")
    FlashcardResponse toResponse(CardState card);

    @Mapping(target = "questionCount", expression = "java(session.questions().size())")
    QuizSessionResponse toResponse(QuizSession session);

    NextQuestionResponse toResponse(QuizService.NextQuestion next);

    @Mapping(target = "score", source = "evaluation.score")
    @Mapping(target = "feedback", source = "evaluation.feedback")
    EvaluationResponse toResponse(QuizService.Graded graded);
}
