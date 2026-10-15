package dev.mindforge.api.dto.response;

import java.util.List;

/** Every live page and the links between paths. */
public record GraphResponse(
    List<IndexEntryResponse> pages,
    List<LinkResponse> links
) {}
