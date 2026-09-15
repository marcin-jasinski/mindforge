package dev.mindforge.infrastructure.persistence.mapper;

import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.infrastructure.persistence.entity.FlashcardEntity;
import dev.mindforge.infrastructure.persistence.entity.QuizSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StudyEntityMapper {

    Flashcard toCard(FlashcardEntity entity);

    @Mapping(target = "card", source = ".")
    CardState toDomain(FlashcardEntity entity);

    QuizSession toDomain(QuizSessionEntity entity);

    QuizSessionEntity toEntity(QuizSession session);
}
