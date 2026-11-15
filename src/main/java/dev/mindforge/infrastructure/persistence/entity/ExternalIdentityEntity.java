package dev.mindforge.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.UUID;

import dev.mindforge.domain.model.ExternalPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/** {@code created_at} is left to the database default. */
@Entity
@Table(name = "external_identities")
@IdClass(ExternalIdentityEntity.Key.class)
public class ExternalIdentityEntity implements Persistable<ExternalIdentityEntity.Key> {

    public record Key(ExternalPlatform platform, String externalId) implements Serializable {}

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", updatable = false, nullable = false, length = 50)
    private ExternalPlatform platform;

    @Id
    @Column(name = "external_id", updatable = false, nullable = false, length = 64)
    private String externalId;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Transient
    private boolean persisted;

    @Override
    public Key getId() { return new Key(platform, externalId); }

    @Override
    public boolean isNew() { return !persisted; }

    @PostPersist
    @PostLoad
    void markPersisted() { persisted = true; }

    public ExternalPlatform getPlatform() { return platform; }
    public void setPlatform(ExternalPlatform platform) { this.platform = platform; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
}
