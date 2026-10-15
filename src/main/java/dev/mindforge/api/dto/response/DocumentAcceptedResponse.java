package dev.mindforge.api.dto.response;

import java.util.UUID;

/** An accepted upload: its run starts after the response. */
public record DocumentAcceptedResponse(
    UUID documentId
) {}
