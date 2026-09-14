package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A record that a section of one page has been corrected by another page. Never alters the superseded prose. */
public record PageSupersession(
    UUID supersessionId,
    UUID supersededPageId,
    String sectionAnchor,
    UUID supersedingPageId,
    UUID ingestRunId,
    Instant createdAt
) {}
