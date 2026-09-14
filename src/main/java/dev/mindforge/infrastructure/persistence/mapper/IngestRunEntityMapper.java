package dev.mindforge.infrastructure.persistence.mapper;

import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.LogEntry;
import dev.mindforge.infrastructure.persistence.entity.IngestRunEntity;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IngestRunEntityMapper {

    /** The {@code upload_source} of a conversation turn, as the partial dedup index in V1 already spells it. */
    String CONVERSATION = "CONVERSATION";

    IngestRun toDomain(IngestRunEntity entity);

    IngestRunEntity toEntity(IngestRun run);

    @Mapping(target = "conversation", expression = "java(CONVERSATION.equals(row.getUploadSource()))")
    @Mapping(target = "reverted", expression = "java(revertedRun(row))")
    LogEntry toLogEntry(IngestRunJpaRepository.LogEntryRow row);

    default LogEntry.RevertedRun revertedRun(IngestRunJpaRepository.LogEntryRow row) {
        if (row.getRevertedKind() == null) {
            return null;
        }
        return new LogEntry.RevertedRun(row.getRevertedKind(), row.getRevertedFinishedAt(),
            CONVERSATION.equals(row.getRevertedUploadSource()), row.getRevertedLessonId(), row.getRevertedLessonTitle());
    }
}
