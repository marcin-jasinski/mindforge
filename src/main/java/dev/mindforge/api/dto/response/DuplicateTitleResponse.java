package dev.mindforge.api.dto.response;

import java.util.List;

/** Live Concepts sharing a title. */
public record DuplicateTitleResponse(
    String title,
    List<String> paths
) {}
