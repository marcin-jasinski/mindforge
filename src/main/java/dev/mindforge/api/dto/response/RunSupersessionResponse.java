package dev.mindforge.api.dto.response;

import java.util.UUID;

/** A supersession the run inserted that still exists; a path is null when its page is gone. */
public record RunSupersessionResponse(
    UUID supersessionId,
    String sectionAnchor,
    String supersededPath,
    String supersedingPath
) {}
