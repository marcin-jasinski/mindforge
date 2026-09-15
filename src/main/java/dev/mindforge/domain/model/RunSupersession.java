package dev.mindforge.domain.model;

import java.util.UUID;

/** A supersession a run inserted and that still exists; a path is null when its page is gone. */
public record RunSupersession(UUID supersessionId, String sectionAnchor, String supersededPath, String supersedingPath) {}
