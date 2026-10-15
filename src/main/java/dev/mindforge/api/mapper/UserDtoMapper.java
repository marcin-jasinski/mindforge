package dev.mindforge.api.mapper;

import dev.mindforge.api.dto.response.UserResponse;
import dev.mindforge.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserDtoMapper {

    UserResponse toResponse(User u);
}
