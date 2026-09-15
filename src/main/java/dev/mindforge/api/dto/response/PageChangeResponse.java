package dev.mindforge.api.dto.response;

import java.util.UUID;

/** A page a run wrote beside its prior revision, null for a page it created. */
public record PageChangeResponse(
    UUID pageId,
    String path,
    RevisionResponse after,
    RevisionResponse before,
    Integer lengthBefore,
    Integer lengthAfter
) {}
