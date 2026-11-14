package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;

import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.infrastructure.persistence.entity.ExternalIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityJpaRepository
    extends JpaRepository<ExternalIdentityEntity, ExternalIdentityEntity.Key> {

    List<ExternalIdentityEntity> findByPlatform(ExternalPlatform platform);
}
