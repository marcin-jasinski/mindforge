package dev.mindforge.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.infrastructure.persistence.entity.InteractionEntity;
import dev.mindforge.infrastructure.persistence.entity.InteractionTurnEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InteractionEntityMapper {

    Interaction toDomain(InteractionEntity entity);

    InteractionEntity toEntity(Interaction interaction);

    InteractionTurn toDomain(InteractionTurnEntity entity);

    InteractionTurnEntity toEntity(InteractionTurn turn, UUID knowledgeBaseId, UUID interactionId);

    default List<String> toPaths(String[] paths) {
        return List.of(paths);
    }

    default String[] toArray(List<String> paths) {
        return paths.toArray(String[]::new);
    }
}
