package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.ExternalIdentity;
import dev.mindforge.domain.model.ExternalIdentityConflictException;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.infrastructure.persistence.entity.ExternalIdentityEntity;
import dev.mindforge.infrastructure.persistence.jpa.ExternalIdentityJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.UserJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.ExternalIdentityEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.UserEntityMapper;

@Transactional
public class ExternalIdentityRepositoryAdapter implements ExternalIdentityRepository {

    private final ExternalIdentityJpaRepository identities;
    private final UserJpaRepository users;
    private final ExternalIdentityEntityMapper mapper;
    private final UserEntityMapper userMapper;

    public ExternalIdentityRepositoryAdapter(ExternalIdentityJpaRepository identities, UserJpaRepository users,
                                             ExternalIdentityEntityMapper mapper, UserEntityMapper userMapper) {
        this.identities = identities;
        this.users = users;
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Override
    public Optional<UUID> findUserId(ExternalPlatform platform, String externalId) {
        return identities.findById(new ExternalIdentityEntity.Key(platform, externalId))
            .map(ExternalIdentityEntity::getUserId);
    }

    /** Flushes both rows so a lost race surfaces here; the exception rolls back the user row with the link. */
    @Override
    public User createUserAndLink(ExternalPlatform platform, String externalId, User user) {
        try {
            User inserted = userMapper.toDomain(users.saveAndFlush(userMapper.toEntity(user)));
            ExternalIdentityEntity identity = new ExternalIdentityEntity();
            identity.setPlatform(platform);
            identity.setExternalId(externalId);
            identity.setUserId(inserted.userId());
            identities.saveAndFlush(identity);
            return inserted;
        } catch (DataIntegrityViolationException e) {
            throw new ExternalIdentityConflictException(platform);
        }
    }

    @Override
    public List<ExternalIdentity> findByPlatform(ExternalPlatform platform) {
        return identities.findByPlatform(platform).stream().map(mapper::toDomain).toList();
    }
}
