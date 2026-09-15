package dev.mindforge.api.dto.response;

import java.util.UUID;

/** A supersession whose section or superseding page is gone. */
public record DanglingSupersessionResponse(
    UUID supersessionId,
    String supersededPath,
    String sectionAnchor,
    String supersedingPath
) {}
