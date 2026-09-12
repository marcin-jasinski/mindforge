package dev.mindforge.api.mapper;

import dev.mindforge.api.dto.response.DocumentResponse;
import dev.mindforge.domain.model.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentDtoMapper {

    @Mapping(target = "lessonId",    source = "lessonIdentity.lessonId")
    @Mapping(target = "lessonTitle", source = "lessonIdentity.title")
    DocumentResponse toResponse(Document d);
}
