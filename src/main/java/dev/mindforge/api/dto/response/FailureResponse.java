package dev.mindforge.api.dto.response;

/** Something a run dropped or skipped; {@code count} is set for dropped or omitted items. */
public record FailureResponse(
    String step,
    String item,
    String path,
    String reason,
    Integer count
) {}
