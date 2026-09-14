package dev.mindforge.domain.model;

import java.util.UUID;

/** A document that contributed to a page, stamped with the run that wrote the contribution. */
public record PageSource(UUID pageId, UUID documentId, UUID ingestRunId) {}
