package dev.mindforge.api.dto.response;

import java.util.UUID;

/** A queued or completed run. */
public record RunAcceptedResponse(
    UUID runId
) {}
