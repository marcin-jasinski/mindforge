package dev.mindforge.infrastructure.persistence.mapper;

import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.infrastructure.persistence.entity.KnowledgeBaseEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface KnowledgeBaseEntityMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    KnowledgeBaseEntity toEntity(KnowledgeBase kb);

    @Mapping(target = "kbId", source = "kbId")
    KnowledgeBase toDomain(KnowledgeBaseEntity e);
}
