package dev.mindforge.domain.model;

import java.util.UUID;

/** A supersession whose two pages are both live, with the superseding page's path and title. */
public record LiveSupersession(
    UUID supersessionId,
    UUID supersededPageId,
    String sectionAnchor,
    UUID supersedingPageId,
    String supersedingPath,
    String supersedingTitle
) {}
