package dev.mindforge.api.dto.response;

/** A link between paths; the target may dangle. */
public record LinkResponse(
    String sourcePath,
    String targetPath
) {}
