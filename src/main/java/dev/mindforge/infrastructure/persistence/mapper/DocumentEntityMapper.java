package dev.mindforge.infrastructure.persistence.mapper;

import java.util.List;

import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.infrastructure.persistence.entity.DocumentEntity;
import dev.mindforge.infrastructure.persistence.jpa.DocumentWithoutBlocks;
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

    /** A document whose {@code contentBlocks} is empty because its row was read without them. */
    default Document toDomain(DocumentWithoutBlocks r) {
        return new Document(r.documentId(), r.knowledgeBaseId(), new LessonIdentity(r.lessonId(), r.lessonTitle()),
            new ContentHash(r.contentHash()), r.sourceFilename(), r.mimeType(), r.originalContent(), List.of(),
            UploadSource.valueOf(r.uploadSource()), r.uploadedBy(), r.createdAt(), r.updatedAt());
    }
}
