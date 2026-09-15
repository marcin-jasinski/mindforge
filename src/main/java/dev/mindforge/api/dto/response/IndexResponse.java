package dev.mindforge.api.dto.response;

import java.util.List;

/** The rendered index and its entries. */
public record IndexResponse(
    String markdown,
    List<IndexEntryResponse> pages
) {}
