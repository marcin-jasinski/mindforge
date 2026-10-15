package dev.mindforge.api.mapper;

import dev.mindforge.api.dto.response.KnowledgeBaseResponse;
import dev.mindforge.domain.model.KnowledgeBase;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KnowledgeBaseDtoMapper {

    KnowledgeBaseResponse toResponse(KnowledgeBase kb);
}
