package dev.mindforge.infrastructure.persistence.mapper;

import dev.mindforge.domain.model.User;
import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserEntityMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity toEntity(User u);

    User toDomain(UserEntity e);
}
