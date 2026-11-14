package dev.mindforge.domain.model;

import java.util.UUID;

/** A chat-platform identity linked to the MindForge user it acts as. */
public record ExternalIdentity(ExternalPlatform platform, String externalId, UUID userId) {}
