package dev.mindforge.domain.model;

import java.util.UUID;

/** A link in a page's body to another page's path, and optionally to one of its sections. Derived from the body. */
public record PageLink(UUID pageId, String targetPath, String fragment) {}
