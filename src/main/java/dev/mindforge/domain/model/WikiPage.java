package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A live page: a code-owned path, title and type, a one-line description, and prose written by the LLM. */
public record WikiPage(
    UUID pageId,
    UUID knowledgeBaseId,
    String path,
    String title,
    String description,
    PageType type,
    String markdownBody,
    int revision,
    Instant createdAt,
    Instant updatedAt
) {}
