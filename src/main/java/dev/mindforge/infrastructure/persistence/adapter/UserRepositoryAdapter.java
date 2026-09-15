package dev.mindforge.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.UserRepository;
import dev.mindforge.infrastructure.persistence.entity.UserEntity;
import dev.mindforge.infrastructure.persistence.jpa.UserJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.UserEntityMapper;

@Transactional
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository users;
    private final UserEntityMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository users, UserEntityMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return users.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public User insert(User user) {
        return mapper.toDomain(users.saveAndFlush(mapper.toEntity(user)));
    }

    @Override
    public void recordLogin(UUID userId, Instant at) {
        load(userId).setLastLoginAt(at);
    }

    @Override
    public User updateDisplayName(UUID userId, String displayName) {
        UserEntity user = load(userId);
        user.setDisplayName(displayName);
        return mapper.toDomain(users.saveAndFlush(user));
    }

    private UserEntity load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }
}
