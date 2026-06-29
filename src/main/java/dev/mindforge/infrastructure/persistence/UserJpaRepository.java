package dev.mindforge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);
}
