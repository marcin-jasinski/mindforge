package dev.mindforge.infrastructure.persistence.mapper;

import java.util.UUID;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.PageGraph;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.infrastructure.persistence.entity.PageLinkEntity;
import dev.mindforge.infrastructure.persistence.entity.PageRevisionEntity;
import dev.mindforge.infrastructure.persistence.entity.PageSourceEntity;
import dev.mindforge.infrastructure.persistence.entity.PageSupersessionEntity;
import dev.mindforge.infrastructure.persistence.entity.WikiPageEntity;
import dev.mindforge.infrastructure.persistence.jpa.WikiPageJpaRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WikiEntityMapper {

    @Mapping(target = "type", source = "pageType")
    WikiPage toDomain(WikiPageEntity entity);

    /** Copies the written state; the store owns the knowledge base, revision and timestamps. */
    @Mapping(target = "pageType", source = "type")
    @Mapping(target = "knowledgeBaseId", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyWrite(PageWrite write, @MappingTarget WikiPageEntity entity);

    @Mapping(target = "type", source = "pageType")
    IndexEntry toIndexEntry(WikiPageJpaRepository.IndexRow row);

    PageGraph.Edge toEdge(WikiPageJpaRepository.EdgeRow row);

    @Mapping(target = "type", source = "pageType")
    PageRevision toDomain(PageRevisionEntity entity);

    @Mapping(target = "pageType", source = "source.type")
    PageRevisionEntity toEntity(PageRevision source, UUID knowledgeBaseId);

    @Mapping(target = "pageId", source = "sourcePageId")
    PageLink toDomain(PageLinkEntity entity);

    @Mapping(target = "linkId", ignore = true)
    @Mapping(target = "sourcePageId", source = "source.pageId")
    PageLinkEntity toEntity(PageLink source, UUID knowledgeBaseId);

    PageSourceEntity toEntity(PageSource source, UUID knowledgeBaseId);

    PageSupersession toDomain(PageSupersessionEntity entity);

    PageSupersessionEntity toEntity(PageSupersession source, UUID knowledgeBaseId);

    default PageType toPageType(String value) {
        return new PageType(value);
    }

    default String fromPageType(PageType type) {
        return type.value();
    }
}
