package dev.mindforge.api.mapper;

import org.mapstruct.Mapper;

import dev.mindforge.api.dto.response.AnswerResponse;
import dev.mindforge.api.dto.response.QuerySessionResponse;
import dev.mindforge.api.dto.response.TurnSummaryResponse;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.TurnSummary;

@Mapper(componentModel = "spring")
public interface QueryDtoMapper {

    QuerySessionResponse toResponse(Interaction interaction);

    AnswerResponse toResponse(QueryService.Answer answer);

    TurnSummaryResponse toResponse(TurnSummary turn);
}
