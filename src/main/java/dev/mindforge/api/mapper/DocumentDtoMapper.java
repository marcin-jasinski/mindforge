package dev.mindforge.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.mindforge.api.dto.response.DocumentResponse;
import dev.mindforge.api.dto.response.RunStateResponse;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.IngestRun;

@Mapper(componentModel = "spring")
public interface DocumentDtoMapper {

    @Mapping(target = "documentId", source = "document.documentId")
    @Mapping(target = "knowledgeBaseId", source = "document.knowledgeBaseId")
    @Mapping(target = "lessonId", source = "document.lessonIdentity.lessonId")
    @Mapping(target = "lessonTitle", source = "document.lessonIdentity.title")
    @Mapping(target = "sourceFilename", source = "document.sourceFilename")
    @Mapping(target = "mimeType", source = "document.mimeType")
    @Mapping(target = "createdAt", source = "document.createdAt")
    DocumentResponse toResponse(IngestionService.DocumentState state);

    RunStateResponse toResponse(IngestRun run);
}
