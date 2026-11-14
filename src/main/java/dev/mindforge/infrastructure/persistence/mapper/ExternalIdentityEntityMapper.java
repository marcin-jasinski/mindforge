package dev.mindforge.infrastructure.persistence.mapper;

import dev.mindforge.domain.model.ExternalIdentity;
import dev.mindforge.infrastructure.persistence.entity.ExternalIdentityEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExternalIdentityEntityMapper {

    ExternalIdentity toDomain(ExternalIdentityEntity e);
}
