package dev.mindforge.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.mindforge.api.dto.response.GraphResponse;
import dev.mindforge.api.dto.response.IndexEntryResponse;
import dev.mindforge.api.dto.response.IndexResponse;
import dev.mindforge.api.dto.response.LinkResponse;
import dev.mindforge.api.dto.response.PageResponse;
import dev.mindforge.api.dto.response.RevisionResponse;
import dev.mindforge.application.service.WikiService;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.PageGraph;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageType;

@Mapper(componentModel = "spring")
public interface WikiDtoMapper {

    IndexEntryResponse toResponse(IndexEntry entry);

    IndexResponse toResponse(WikiService.IndexView index);

    @Mapping(target = "pageId", source = "page.pageId")
    @Mapping(target = "path", source = "page.path")
    @Mapping(target = "title", source = "page.title")
    @Mapping(target = "description", source = "page.description")
    @Mapping(target = "type", source = "page.type")
    @Mapping(target = "revision", source = "page.revision")
    @Mapping(target = "createdAt", source = "page.createdAt")
    @Mapping(target = "updatedAt", source = "page.updatedAt")
    PageResponse toResponse(WikiService.PageView view);

    RevisionResponse toResponse(PageRevision revision);

    GraphResponse toResponse(PageGraph graph);

    LinkResponse toResponse(PageGraph.Edge edge);

    default String toType(PageType type) {
        return type.value();
    }
}
