package dev.mindforge.api.mapper;

import dev.mindforge.api.dto.response.ArtifactResponse;
import dev.mindforge.domain.model.DocumentArtifact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArtifactDtoMapper {

    @Mapping(target = "summary",    source = "summary.summary")
    @Mapping(target = "keyPoints",  source = "summary.keyPoints")
    ArtifactResponse toResponse(DocumentArtifact a);
}
