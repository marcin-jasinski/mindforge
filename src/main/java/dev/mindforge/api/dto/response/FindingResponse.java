package dev.mindforge.api.dto.response;

import java.util.List;

/** A Lint finding or suggestion naming the pages involved. */
public record FindingResponse(
    String kind,
    List<String> pages,
    String text
) {}
