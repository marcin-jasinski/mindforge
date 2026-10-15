package dev.mindforge.api.dto.response;

/** A dangling link; {@code livePath} names the page a wrong-directory link probably meant. */
public record LinkFindingResponse(
    String sourcePath,
    String targetPath,
    String livePath
) {}
