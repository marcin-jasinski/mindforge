package dev.mindforge.api.dto.response;

/** One live page as the index lists it. */
public record IndexEntryResponse(
    String path,
    String title,
    String description,
    String type
) {}
