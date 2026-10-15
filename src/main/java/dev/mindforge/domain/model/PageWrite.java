package dev.mindforge.domain.model;

import java.util.UUID;

/** The state a page has after a write: a new page when no live page has {@code pageId}, a revision otherwise. */
public record PageWrite(
    UUID pageId,
    String path,
    String title,
    String description,
    PageType type,
    String markdownBody
) {}
