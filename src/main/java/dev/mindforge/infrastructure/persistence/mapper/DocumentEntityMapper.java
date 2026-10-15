package dev.mindforge.infrastructure.persistence.mapper;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.infrastructure.persistence.entity.DocumentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {
    LessonIdentity.class, ContentHash.class, UploadSource.class
})
public interface DocumentEntityMapper {

    @Mapping(target = "lessonId",     source = "lessonIdentity.lessonId")
    @Mapping(target = "lessonTitle",  source = "lessonIdentity.title")
    @Mapping(target = "contentHash",  source = "contentHash.sha256")
    @Mapping(target = "uploadSource", expression = "java(d.uploadSource().name())")
    DocumentEntity toEntity(Document d);

    @Mapping(target = "lessonIdentity",
        expression = "java(new LessonIdentity(e.getLessonId(), e.getLessonTitle()))")
    @Mapping(target = "contentHash",
        expression = "java(new ContentHash(e.getContentHash()))")
    @Mapping(target = "uploadSource",
        expression = "java(UploadSource.valueOf(e.getUploadSource()))")
    Document toDomain(DocumentEntity e);
}
