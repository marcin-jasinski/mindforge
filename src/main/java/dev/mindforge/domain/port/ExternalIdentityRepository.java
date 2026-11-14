package dev.mindforge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.ExternalIdentity;
import dev.mindforge.domain.model.ExternalIdentityConflictException;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.User;

/** Chat-platform identities linked to accounts. Not tenant-scoped: an identity belongs to a user, not a knowledge base. */
public interface ExternalIdentityRepository {

    Optional<UUID> findUserId(ExternalPlatform platform, String externalId);

    /**
     * Inserts the user and its link in one transaction.
     *
     * @throws ExternalIdentityConflictException when the identity is already linked or the email is taken; neither
     *     row is written
     */
    User createUserAndLink(ExternalPlatform platform, String externalId, User user);

    List<ExternalIdentity> findByPlatform(ExternalPlatform platform);
}
